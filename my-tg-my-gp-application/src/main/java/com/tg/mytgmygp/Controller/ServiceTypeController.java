package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.ServiceTypeResponse;
import com.tg.mytgmygp.Service.ServiceTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    public ServiceTypeController(
            ServiceTypeService serviceTypeService) {

        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping
    public List<ServiceTypeResponse> getServices() {

        return serviceTypeService.getActiveServices();
    }
}