package com.hostel.ordering.pms;

import com.hostel.ordering.pms.dto.CleaningTaskList;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only cleaning to-do. Housekeeping and admins; SuperAdmin passes via the role hierarchy. */
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
}
