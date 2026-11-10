package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.pms.PmsWriteClaim.State;
import com.hostel.ordering.service.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PmsWriteGuardTest {

    record Moved(String bookingId, String room) {}

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    private static final String F = "checkin";
    private static final String HASH = PmsWriteGuard.hash("B1", "101");

    private final PmsProperties props = new PmsProperties();
    private final InMemoryPmsWriteClaimStore store = new InMemoryPmsWriteClaimStore();
    private final AuditService audit = mock(AuditService.class);
    private final AtomicInteger calls = new AtomicInteger();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private PmsWriteGuard guard;

    @BeforeEach
    void setUp() {
        props.getWrites().put(F, true);
        guard = guardAt(T0);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private PmsWriteGuard guardAt(Instant now) {
        return new PmsWriteGuard(new PmsFlags(props), store, audit, new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private Supplier<Moved> ok(String booking) {
        return () -> {
            calls.incrementAndGet();
            return new Moved(booking, "101");
        };
    }

    private PmsWriteResult<Moved> run(PmsWriteGuard g, String key, String target, String hash, Supplier<Moved> call) {
        return g.execute(F, target, key, hash, "CHECKIN", "booking=" + target, Moved.class, call, m -> true);
    }

    private PmsWriteResult<Moved> run(String key, String target, Supplier<Moved> call) {
        return run(guard, key, target, HASH, call);
    }

    /** An eZee call that signals when it starts and waits until released. */
    private Supplier<Moved> blocking(CountDownLatch entered, CountDownLatch release) {
        return () -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return new Moved("B1", "101");
        };
    }

    @Test
    void blankKeyIsRefusedBeforeEzee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run(" ", "B1", ok("B1")));
        assertEquals("Idempotency-Key is required", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> run(null, "B1", ok("B1")));
        assertEquals(0, calls.get());
        assertEquals(0, store.size());
    }

    @Test
    void keyThatIsNotAnIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> run("Room 101 for Jane Doe", "B1", ok("B1")));
        assertThrows(IllegalArgumentException.class, () -> run("k".repeat(129), "B1", ok("B1")));
        assertEquals(0, calls.get());
    }

    @Test
    void flagOffIsRefusedBeforeEzee() {
        props.getWrites().put(F, false);
        assertThrows(PmsFeatureDisabledException.class, () -> run("k1", "B1", ok("B1")));
        props.getWrites().clear();
        assertThrows(PmsFeatureDisabledException.class, () -> run("k1", "B1", ok("B1")));
        assertEquals(0, calls.get());
        assertEquals(0, store.size());
        verifyNoInteractions(audit);
    }

    @Test
    void canaryExcludingTheTargetBlocksBeforeEzee() {
        props.getCanary().put(F, List.of("B1"));
        assertThrows(PmsFeatureDisabledException.class, () -> run("k1", "B2", ok("B2")));
        assertEquals(0, calls.get());
        assertTrue(run("k2", "B1", ok("B1")).confirmed());
        assertEquals(1, calls.get());
    }

    @Test
    void emptyCanaryAllowsAnyTarget() {
        props.getCanary().put(F, List.of());
        assertTrue(run("k1", "B7", ok("B7")).confirmed());
        assertTrue(run("k2", "B8", ok("B8")).confirmed());
        assertEquals(2, calls.get());
    }

    @Test
    void successStoresTheResultUnlocksTheTargetAndAuditsOnlyTheCallersIds() {
        PmsWriteResult<Moved> r = run("k1", "B1", ok("B1"));

        assertTrue(r.confirmed());
        assertEquals(new Moved("B1", "101"), r.value());
        PmsWriteClaim row = store.find("k1");
        assertEquals(State.DONE, row.getState());
        assertNull(row.getOpenLockKey());
        assertEquals(T0, row.getDoneAt());
        assertNotNull(row.getResultJson());
        verify(audit).logAction("PMS_CHECKIN", "booking=B1");
        verifyNoMoreInteractions(audit);
        // the target is free for the next write
        assertTrue(run("k2", "B1", ok("B1")).confirmed());
    }

    @Test
    void manyThreadsWithTheSameKeyCallEzeeExactlyOnce() throws Exception {
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch losersDone = new CountDownLatch(n - 1);
        Supplier<Moved> call = blocking(entered, release);
        List<Future<PmsWriteResult<Moved>>> futures = new ArrayList<>();
        AtomicInteger inProgress = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return run("same", "B1", call);
                } catch (PmsInProgressException e) {
                    inProgress.incrementAndGet();
                    losersDone.countDown();
                    return null;
                }
            }));
        }
        start.countDown();
        assertTrue(losersDone.await(5, TimeUnit.SECONDS));
        release.countDown();
        int winners = 0;
        for (Future<PmsWriteResult<Moved>> f : futures) {
            if (f.get(5, TimeUnit.SECONDS) != null) {
                winners++;
            }
        }
        assertEquals(1, winners);
        assertEquals(n - 1, inProgress.get());
        assertEquals(1, calls.get());
    }

    @Test
    void sameKeyWhileTheFirstIsInFlightGetsInProgress() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<PmsWriteResult<Moved>> first = pool.submit(() -> run("k1", "B1", blocking(entered, release)));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertThrows(PmsInProgressException.class, () -> run("k1", "B1", ok("B1")));

        release.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS).confirmed());
        assertEquals(1, calls.get());
    }

    @Test
    void differentKeysOnTheSameTargetTheSecondGetsTargetBusy() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<PmsWriteResult<Moved>> first = pool.submit(() -> run("k1", "B1", blocking(entered, release)));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertThrows(PmsTargetBusyException.class, () -> run("k2", "B1", ok("B1")));
        assertNull(store.find("k2"));

        release.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS).confirmed());
        assertEquals(1, calls.get());
    }

    @Test
    void repeatOfADoneKeyReplaysTheStoredResultWithoutCallingEzee() {
        PmsWriteResult<Moved> first = run("k1", "B1", ok("B1"));
        PmsWriteResult<Moved> again = run("k1", "B1", ok("B1"));

        assertEquals(first.value(), again.value());
        assertTrue(again.confirmed());
        assertEquals(1, calls.get());
        verify(audit).logAction("PMS_CHECKIN", "booking=B1");
        verifyNoMoreInteractions(audit);
    }

    @Test
    void repeatOfAKeyWithADifferentRequestIsAMismatch() {
        run("k1", "B1", ok("B1"));
        assertThrows(PmsIdempotencyMismatchException.class,
                () -> run(guard, "k1", "B1", PmsWriteGuard.hash("B1", "102"), ok("B1")));
        // same hash but a different target is also a different request
        assertThrows(PmsIdempotencyMismatchException.class, () -> run("k1", "B2", ok("B2")));
        assertEquals(1, calls.get());
    }

    @Test
    void upstreamFailureLeavesOneUnconfirmedClaimThatBlocksTheTargetUntilResolved() {
        PmsUnconfirmedException e = assertThrows(PmsUnconfirmedException.class,
                () -> run("k1", "B1", () -> {
                    calls.incrementAndGet();
                    throw new PmsUpstreamException("PMS request timed out");
                }));
        assertFalse(e.getMessage().contains("timed out"));

        assertEquals(1, store.size());
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
        verifyNoMoreInteractions(audit);

        assertThrows(PmsTargetBusyException.class, () -> run("k2", "B1", ok("B1")));
        assertThrows(PmsUnconfirmedException.class, () -> run("k1", "B1", ok("B1")));
        assertEquals(1, calls.get());

        assertTrue(guard.resolve("k1", false));
        assertNull(store.find("k1"));
        assertTrue(run("k2", "B1", ok("B1")).confirmed());
    }

    @Test
    void anyOtherRuntimeFailureIsAlsoAnUnknownOutcome() {
        assertThrows(PmsUnconfirmedException.class, () -> run("k1", "B1", () -> {
            throw new IllegalStateException("socket reset");
        }));
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
    }

    @Test
    void definiteFailuresReleaseTheTargetAndRethrowUnchanged() {
        List<RuntimeException> definite = List.of(new PmsBusyException("busy"), new PmsAuthException("auth"),
                new PmsNotEnabledException("off"), new PmsFeatureDisabledException("off"));
        for (RuntimeException failure : definite) {
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> run("k1", "B1", () -> {
                throw failure;
            }));
            assertSame(failure, thrown);
            assertEquals(0, store.size(), failure.getClass().getSimpleName());
        }
        verifyNoInteractions(audit);
        assertTrue(run("k1", "B1", ok("B1")).confirmed());
    }

    /** A real gateway over a fake transport, so the guard sees the exceptions the gateway really throws. */
    private EzeeGateway gatewayWith(PmsProperties gatewayProps, EzeeGatewayTest.FakeTransport transport) {
        gatewayProps.setHubPer5s(1000);
        return new EzeeGateway(gatewayProps, new PmsRateLimiter(gatewayProps), transport, "https://pms.test", "K", "H");
    }

    private void assertNotSentFailureLeavesNoTrace(Supplier<Moved> call) {
        PmsUpstreamException e = assertThrows(PmsUpstreamException.class, () -> run("k1", "B1", call));
        assertFalse(e.isSent());
        assertEquals(0, store.size());
        verifyNoInteractions(audit);
        // nothing is locked: the next write to the same target goes ahead
        assertTrue(run("k2", "B1", ok("B1")).confirmed());
        store.delete("k2", State.DONE);
        org.mockito.Mockito.reset(audit);
    }

    @Test
    void killSwitchOffIsADefiniteFailureNoClaimNoAuditNoLock() {
        PmsProperties off = new PmsProperties();
        off.setEnabled(false);
        EzeeGatewayTest.FakeTransport transport = new EzeeGatewayTest.FakeTransport();
        EzeeGateway gateway = gatewayWith(off, transport);
        assertNotSentFailureLeavesNoTrace(() -> {
            gateway.json(EzeeFamily.HOUSEKEEPING, "UpdateRoomStatus", Map.of(), PmsPriority.HUB);
            return new Moved("B1", "101");
        });
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void unknownRequestTypeAndBadXmlParamAreDefiniteFailures() {
        EzeeGatewayTest.FakeTransport transport = new EzeeGatewayTest.FakeTransport();
        EzeeGateway gateway = gatewayWith(new PmsProperties(), transport);
        assertNotSentFailureLeavesNoTrace(() -> {
            gateway.text(EzeeFamily.HOUSEKEEPING, "NoSuchType", Map.of(), PmsPriority.HUB);
            return new Moved("B1", "101");
        });
        assertNotSentFailureLeavesNoTrace(() -> {
            gateway.text(EzeeFamily.POS2PMS, "postcharge", Map.of("<bad>", "1"), PmsPriority.HUB);
            return new Moved("B1", "101");
        });
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void aTransportFailureThroughTheGatewayStillParksUnconfirmed() {
        EzeeGatewayTest.FakeTransport transport = new EzeeGatewayTest.FakeTransport();
        transport.failure = new IllegalStateException("socket reset");
        EzeeGateway gateway = gatewayWith(new PmsProperties(), transport);
        assertThrows(PmsUnconfirmedException.class, () -> run("k1", "B1", () -> {
            gateway.text(EzeeFamily.HOUSEKEEPING, "UpdateRoomStatus", Map.of(), PmsPriority.HUB);
            return new Moved("B1", "101");
        }));
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
        assertThrows(PmsTargetBusyException.class, () -> run("k2", "B1", ok("B1")));
    }

    // M-3: target and request hash are part of the claim, so blank ones are refused before anything happens.
    @Test
    void blankTargetOrRequestHashIsRefusedBeforeEzee() {
        for (String blank : new String[] {null, " "}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> run(guard, "k1", blank, HASH, ok("B1")));
            assertEquals("targetId is required", e.getMessage());
            e = assertThrows(IllegalArgumentException.class, () -> run(guard, "k1", "B1", blank, ok("B1")));
            assertEquals("requestHash is required", e.getMessage());
        }
        assertEquals(0, calls.get());
        assertEquals(0, store.size());
        verifyNoInteractions(audit);
    }

    /** The in-memory store with switchable failures for the store-failure paths (I-4). */
    static class FailingStore extends InMemoryPmsWriteClaimStore {
        boolean failMarkDone;
        boolean failMarkUnconfirmed;
        boolean failDelete;

        @Override
        public synchronized PmsWriteClaim markDone(String id, State from, String resultJson, Instant doneAt) {
            if (failMarkDone) {
                throw new IllegalStateException("mongo down");
            }
            return super.markDone(id, from, resultJson, doneAt);
        }

        @Override
        public synchronized PmsWriteClaim markUnconfirmed(String id, Instant createdBefore) {
            if (failMarkUnconfirmed) {
                throw new IllegalStateException("mongo down");
            }
            return super.markUnconfirmed(id, createdBefore);
        }

        @Override
        public synchronized PmsWriteClaim delete(String id, State state) {
            if (failDelete) {
                throw new IllegalStateException("mongo down");
            }
            return super.delete(id, state);
        }
    }

    private final FailingStore failing = new FailingStore();

    private PmsWriteGuard failingGuard(Instant now) {
        return new PmsWriteGuard(new PmsFlags(props), failing, audit, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void markDoneFailureAfterASuccessfulCallIsUnconfirmedAndAudited() {
        failing.failMarkDone = true;
        PmsUnconfirmedException e = assertThrows(PmsUnconfirmedException.class,
                () -> run(failingGuard(T0), "k1", "B1", HASH, ok("B1")));
        assertEquals("PMS write outcome unknown", e.getMessage());
        assertEquals(1, calls.get());
        // parked straight away instead of waiting for the stuck sweep
        assertEquals(State.UNCONFIRMED, failing.find("k1").getState());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
        verify(audit, never()).logAction("PMS_CHECKIN", "booking=B1");
    }

    @Test
    void markDoneAndParkBothFailingIsStillUnconfirmed() {
        failing.failMarkDone = true;
        failing.failMarkUnconfirmed = true;
        assertThrows(PmsUnconfirmedException.class, () -> run(failingGuard(T0), "k1", "B1", HASH, ok("B1")));
        assertEquals(1, calls.get());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
    }

    @Test
    void markUnconfirmedFailureOnAnAmbiguousCallIsUnconfirmedNotTheStoreError() {
        failing.failMarkUnconfirmed = true;
        PmsUnconfirmedException e = assertThrows(PmsUnconfirmedException.class,
                () -> run(failingGuard(T0), "k1", "B1", HASH, () -> {
                    calls.incrementAndGet();
                    throw new PmsUpstreamException("PMS request timed out");
                }));
        assertEquals("PMS write outcome unknown", e.getMessage());
        assertNull(e.getCause());
        assertEquals(1, calls.get());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
    }

    @Test
    void markUnconfirmedFailureOnAReadBackMismatchIsUnconfirmed() {
        failing.failMarkUnconfirmed = true;
        assertThrows(PmsUnconfirmedException.class, () -> failingGuard(T0).execute(F, "B1", "k1", HASH, "CHECKIN",
                "booking=B1", Moved.class, ok("B1"), m -> false));
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
    }

    @Test
    void anAuditFailureDoesNotMaskTheUnconfirmedOutcome() {
        failing.failMarkUnconfirmed = true;
        org.mockito.Mockito.doThrow(new IllegalStateException("audit down")).when(audit)
                .logAction(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThrows(PmsUnconfirmedException.class, () -> run(failingGuard(T0), "k1", "B1", HASH, () -> {
            throw new PmsUpstreamException("PMS request timed out");
        }));
    }

    @Test
    void anAuditFailureAfterAConfirmedWriteStillReturnsTheResult() {
        org.mockito.Mockito.doThrow(new IllegalStateException("audit down")).when(audit)
                .logAction(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        PmsWriteResult<Moved> r = run("k1", "B1", ok("B1"));
        assertTrue(r.confirmed());
        assertEquals(State.DONE, store.find("k1").getState());
    }

    @Test
    void deleteFailureOnADefiniteFailureStillRethrowsTheOriginal() {
        failing.failDelete = true;
        PmsAuthException auth = new PmsAuthException("auth");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> run(failingGuard(T0), "k1", "B1", HASH, () -> {
                    throw auth;
                }));
        assertSame(auth, thrown);
        PmsUpstreamException notSent = PmsUpstreamException.notSent("PMS integration disabled");
        thrown = assertThrows(RuntimeException.class, () -> run(failingGuard(T0), "k2", "B2", HASH, () -> {
            throw notSent;
        }));
        assertSame(notSent, thrown);
        verifyNoInteractions(audit);
    }

    @Test
    void aStuckParkFailureOnRetryIsStillUnconfirmed() {
        PmsWriteClaim c = new PmsWriteClaim("k1", F, "B1", HASH, "CHECKIN", T0);
        c.setOpenLockKey(F + ":B1");
        c.setState(State.IN_PROGRESS);
        failing.insert(c);
        failing.failMarkUnconfirmed = true;
        assertThrows(PmsUnconfirmedException.class,
                () -> run(failingGuard(T0.plus(Duration.ofMinutes(6))), "k1", "B1", HASH, ok("B1")));
        assertEquals(0, calls.get());
    }

    @Test
    void aCallThatOutlivesTheStuckBoundReturnsUnconfirmedAndKeepsTheLock() {
        PmsWriteGuard later = guardAt(T0.plus(Duration.ofMinutes(6)));
        PmsWriteResult<Moved> r = run("k1", "B1", () -> {
            calls.incrementAndGet();
            later.unconfirmed(); // another request's listing parks this claim while eZee is still answering
            return new Moved("B1", "101");
        });
        assertFalse(r.confirmed());
        assertEquals(new Moved("B1", "101"), r.value());
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "idempotencyKey=k1 targetId=B1");
        verify(audit, never()).logAction("PMS_CHECKIN", "booking=B1");
        assertThrows(PmsTargetBusyException.class, () -> run("k2", "B1", ok("B1")));
    }

    @Test
    void readBackFalseMarksUnconfirmedAndReturnsUnconfirmedResult() {
        PmsWriteResult<Moved> r = guard.execute(F, "B1", "k1", HASH, "CHECKIN", "booking=B1", Moved.class,
                ok("B1"), m -> false);

        assertFalse(r.confirmed());
        assertEquals(new Moved("B1", "101"), r.value());
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        assertNull(store.find("k1").getResultJson());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "booking=B1");
        verify(audit, never()).logAction("PMS_CHECKIN", "booking=B1");
        assertThrows(PmsUnconfirmedException.class, () -> run("k1", "B1", ok("B1")));
    }

    @Test
    void readBackThatThrowsCountsAsUnconfirmed() {
        PmsWriteResult<Moved> r = guard.execute(F, "B1", "k1", HASH, "CHECKIN", "booking=B1", Moved.class,
                ok("B1"), m -> {
                    throw new PmsBusyException("busy");
                });
        assertFalse(r.confirmed());
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
    }

    @Test
    void resolveAppliedMarksDoneAndFreesTheTarget() {
        assertThrows(PmsUnconfirmedException.class, () -> run("k1", "B1", () -> {
            throw new PmsUpstreamException("timeout");
        }));

        assertTrue(guard.resolve("k1", true));

        PmsWriteClaim row = store.find("k1");
        assertEquals(State.DONE, row.getState());
        assertNull(row.getOpenLockKey());
        assertEquals(T0, row.getDoneAt());
        verify(audit).logAction("PMS_CHECKIN_RESOLVED", "idempotencyKey=k1 targetId=B1 outcome=APPLIED");
        assertTrue(run("k2", "B1", ok("B1")).confirmed());
        // a second resolve finds nothing open
        assertFalse(guard.resolve("k1", true));
    }

    @Test
    void resolveOfAnUnknownOrNonUnconfirmedKeyChangesNothing() {
        assertFalse(guard.resolve("nope", false));
        run("k1", "B1", ok("B1"));
        assertFalse(guard.resolve("k1", false));
        assertEquals(State.DONE, store.find("k1").getState());
    }

    private void crashedClaim(String key, String target, Instant createdAt) {
        PmsWriteClaim c = new PmsWriteClaim(key, F, target, HASH, "CHECKIN", createdAt);
        c.setOpenLockKey(F + ":" + target);
        c.setState(State.IN_PROGRESS);
        store.insert(c);
    }

    @Test
    void aFreshInProgressClaimStaysInProgress() {
        crashedClaim("k1", "B1", T0);
        PmsWriteGuard later = guardAt(T0.plus(Duration.ofMinutes(4)));
        assertThrows(PmsInProgressException.class, () -> run(later, "k1", "B1", HASH, ok("B1")));
        assertThrows(PmsTargetBusyException.class, () -> run(later, "k2", "B1", HASH, ok("B1")));
        assertEquals(State.IN_PROGRESS, store.find("k1").getState());
        assertTrue(later.unconfirmed().isEmpty());
        verifyNoInteractions(audit);
    }

    @Test
    void aStuckInProgressClaimBecomesUnconfirmedWhenTheSameKeyRetries() {
        crashedClaim("k1", "B1", T0);
        PmsWriteGuard later = guardAt(T0.plus(Duration.ofMinutes(6)));

        assertThrows(PmsUnconfirmedException.class, () -> run(later, "k1", "B1", HASH, ok("B1")));

        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        verify(audit).logAction("PMS_CHECKIN_UNCONFIRMED", "idempotencyKey=k1 targetId=B1");
        assertEquals(0, calls.get());
    }

    @Test
    void aStuckInProgressClaimBecomesUnconfirmedWhenAnotherKeyHitsTheTarget() {
        crashedClaim("k1", "B1", T0);
        PmsWriteGuard later = guardAt(T0.plus(Duration.ofMinutes(6)));

        assertThrows(PmsTargetBusyException.class, () -> run(later, "k2", "B1", HASH, ok("B1")));

        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
        assertEquals(0, calls.get());
    }

    @Test
    void listingSurfacesStuckClaimsAsUnconfirmed() {
        crashedClaim("k1", "B1", T0);
        PmsWriteGuard later = guardAt(T0.plus(Duration.ofMinutes(6)));

        List<PmsWriteClaim> open = later.unconfirmed();

        assertEquals(1, open.size());
        assertEquals("k1", open.get(0).getId());
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
    }

    @Test
    void flagsAreReadOnlyThroughPmsFlags() {
        PmsFlags flags = new PmsFlags(props);
        assertTrue(flags.isWriteEnabled(F));
        assertFalse(flags.isWriteEnabled("checkout"));
        assertThrows(PmsFeatureDisabledException.class, () -> flags.requireWrite("checkout"));
        props.getCanary().put(F, List.of("B1"));
        assertEquals(List.of("B1"), flags.canary(F));
        assertEquals(List.of(), flags.canary("checkout"));
        props.setWrites(Map.of());
        assertFalse(flags.isWriteEnabled(F));
    }

    @Test
    void hashIsStableAndSeparatesParts() {
        assertEquals(PmsWriteGuard.hash("a", "bc"), PmsWriteGuard.hash("a", "bc"));
        assertNotEquals(PmsWriteGuard.hash("a", "bc"), PmsWriteGuard.hash("ab", "c"));
        assertEquals(64, PmsWriteGuard.hash("x").length());
    }
}
