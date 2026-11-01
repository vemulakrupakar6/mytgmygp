package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.ServiceTypeResponse;
import com.tg.mytgmygp.Repository.ServiceTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;

    public ServiceTypeService(
            ServiceTypeRepository serviceTypeRepository) {

        this.serviceTypeRepository = serviceTypeRepository;
    }

    public List<ServiceTypeResponse> getActiveServices() {

        return serviceTypeRepository
                .findByActiveTrueOrderByServiceIdAsc()
                .stream()
                .map(service -> new ServiceTypeResponse(
                        service.getServiceId(),
                        service.getServiceCode(),
                        service.getServiceName()
                ))
                .toList();
    }
}