package com.hostel.ordering.pms;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Café charge reconciliation. Admins only (it shows guest names); SuperAdmin passes via the role hierarchy. */
@RestController
@RequestMapping("/pms/reconcile")
@PreAuthorize("hasRole('ADMIN')")
public class PmsReconcileController {

    private final PmsReconcileService service;

    public PmsReconcileController(PmsReconcileService service) {
        this.service = service;
    }

    /** {items, asOf, stale, truncated} for the orders of the IST day {@code date} (yyyy-MM-dd, default today). */
    @GetMapping("/charges")
    public PmsReconcileService.Result charges(@RequestParam(required = false) String date) {
        return service.charges(date);
    }
}
