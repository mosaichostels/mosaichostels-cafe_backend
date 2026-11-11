package com.hostel.ordering.pms;

import com.hostel.ordering.model.CleaningAssignment;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cleaning to-do and assignments. Housekeeping and admins; SuperAdmin passes via the role hierarchy. */
@RestController
@RequestMapping("/pms")
@PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
public class PmsHousekeepingWorkflowController {

    private final PmsHousekeepingWorkflowService service;

    public PmsHousekeepingWorkflowController(PmsHousekeepingWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/cleaning-tasks")
    public CleaningTaskList tasks(@RequestParam(required = false) String date) {
        return service.tasks(date);
    }

    @PostMapping("/cleaning-tasks/{taskKey}/assign")
    public CleaningAssignment assign(@PathVariable String taskKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PmsHousekeepingWorkflowService.AssignRequest body) {
        return service.assign(taskKey, body, idempotencyKey);
    }

    @PutMapping("/cleaning-assignments/{id}")
    public CleaningAssignment update(@PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PmsHousekeepingWorkflowService.UpdateRequest body) {
        return service.update(id, body, idempotencyKey);
    }
}
