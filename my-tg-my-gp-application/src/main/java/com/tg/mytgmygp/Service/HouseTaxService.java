package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.*;
import com.tg.mytgmygp.Entity.*;
import com.tg.mytgmygp.Repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HouseTaxService {

    private final HouseTaxAssessmentRepository assessmentRepository;
    private final HouseTaxCurrentDemandRepository currentDemandRepository;
    private final HouseTaxArrearDetailsRepository arrearRepository;
    private final DistrictRepository districtRepository;
    private final MandalRepository mandalRepository;
    private final GramPanchayatRepository gramPanchayatRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public HouseTaxService(
            HouseTaxAssessmentRepository assessmentRepository,
            HouseTaxCurrentDemandRepository currentDemandRepository,
            HouseTaxArrearDetailsRepository arrearRepository,
            DistrictRepository districtRepository,
            MandalRepository mandalRepository,
            GramPanchayatRepository gramPanchayatRepository,
            ServiceTypeRepository serviceTypeRepository) {

        this.assessmentRepository = assessmentRepository;
        this.currentDemandRepository = currentDemandRepository;
        this.arrearRepository = arrearRepository;
        this.districtRepository = districtRepository;
        this.mandalRepository = mandalRepository;
        this.gramPanchayatRepository = gramPanchayatRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    public HouseTaxSearchResponse search(HouseTaxSearchRequest request) {

        validateLocationAndService(
                request.districtId(),
                request.mandalId(),
                request.gramPanchayatId(),
                request.serviceCode()
        );

        if (request.searchMethod() == null ||
                request.searchValue() == null ||
                request.searchValue().isBlank()) {
            throw new IllegalArgumentException(
                    "Search method and search value are required."
            );
        }

        String method = request.searchMethod().trim().toUpperCase();
        String value = request.searchValue().trim();

        List<house_tax_assessments> matches;

        if ("ASSESSMENT_NO".equals(method)) {

            if (!value.matches("\\d+")) {
                throw new IllegalArgumentException(
                        "Assessment number must contain digits only."
                );
            }

            Long assessmentNo = Long.valueOf(value);

            matches = assessmentRepository
                    .findByAssessmentNoAndDistrictIdAndMandalIdAndGramPanchayatId(
                            assessmentNo,
                            request.districtId(),
                            request.mandalId(),
                            request.gramPanchayatId()
                    )
                    .map(List::of)
                    .orElseGet(List::of);

        } else if ("OWNER_NAME".equals(method)) {

            if (value.length() < 2) {
                throw new IllegalArgumentException(
                        "Enter at least two characters of the owner name or surname."
                );
            }

            matches = assessmentRepository.searchByOwnerOrFatherName(
                    value,
                    request.districtId(),
                    request.mandalId(),
                    request.gramPanchayatId()
            );

        } else {
            throw new IllegalArgumentException(
                    "Invalid search method. Use ASSESSMENT_NO or OWNER_NAME."
            );
        }

        List<HouseTaxSearchItem> items = matches.stream()
                .map(h -> new HouseTaxSearchItem(
                        h.getAssessmentNo(),
                        h.getPtin(),
                        h.getOwner(),
                        h.getFatherHusbandName(),
                        h.getHouseNo(),
                        h.getBuildingUsage()
                ))
                .toList();

        if (matches.isEmpty()) {
            return new HouseTaxSearchResponse(
                    "NOT_FOUND",
                    "No House Tax records found for the selected location and search value.",
                    List.of(),
                    null
            );
        }

        if (matches.size() > 1) {
            return new HouseTaxSearchResponse(
                    "MULTIPLE_MATCHES",
                    "Multiple properties found. Select your property to view the complete details.",
                    items,
                    null
            );
        }

        HouseTaxDetailsRequest detailsRequest =
                new HouseTaxDetailsRequest(
                        request.districtId(),
                        request.mandalId(),
                        request.gramPanchayatId(),
                        request.serviceCode(),
                        matches.get(0).getAssessmentNo(),
                        method,
                        value
                );

        HouseTaxDetailsResponse details = getDetails(detailsRequest);

        return new HouseTaxSearchResponse(
                "SINGLE_MATCH",
                "One property found.",
                items,
                details
        );
    }

    public HouseTaxDetailsResponse getDetails(
            HouseTaxDetailsRequest request) {

        validateLocationAndService(
                request.districtId(),
                request.mandalId(),
                request.gramPanchayatId(),
                request.serviceCode()
        );

        house_tax_assessments assessment = assessmentRepository
                .findByAssessmentNoAndDistrictIdAndMandalIdAndGramPanchayatId(
                        request.assessmentNo(),
                        request.districtId(),
                        request.mandalId(),
                        request.gramPanchayatId()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "The selected assessment does not belong to the selected location."
                ));

        var district = districtRepository.findById(request.districtId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "District not found."
                ));

        var mandal = mandalRepository.findById(request.mandalId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mandal not found."
                ));

        var gp = gramPanchayatRepository
                .findById(request.gramPanchayatId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Gram Panchayat not found."
                ));

        // Verify that the selected IDs form a valid hierarchy.
        if (!mandal.getDistrict().getDistrictId()
                .equals(request.districtId())) {
            throw new IllegalArgumentException(
                    "The selected Mandal does not belong to the selected District."
            );
        }

        if (!gp.getMandal().getMandalId()
                .equals(request.mandalId())) {
            throw new IllegalArgumentException(
                    "The selected Gram Panchayat does not belong to the selected Mandal."
            );
        }

        var serviceType = serviceTypeRepository
                .findByServiceCodeAndActiveTrue(request.serviceCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected service is not active."
                ));

        var currentDemand = currentDemandRepository
                .findById(request.assessmentNo())
                .orElse(null);

        var arrear = arrearRepository
                .findById(request.assessmentNo())
                .orElse(null);

        BigDecimal currentAmount = currentDemand == null
                ? BigDecimal.ZERO
                : currentDemand.getTotalAmount();

        BigDecimal arrearAmount = arrear == null
                ? BigDecimal.ZERO
                : arrear.getTotalAmount();

        BigDecimal total = currentAmount
                .add(arrearAmount);

        return new HouseTaxDetailsResponse(
                request.searchMethod(),
                request.searchValue(),
                serviceType.getServiceCode(),
                serviceType.getServiceName(),
                district.getDistrictName(),
                mandal.getMandalName(),
                gp.getGramPanchayatName(),
                assessment,
                currentDemand,
                arrear,
                total
        );
    }

    private void validateLocationAndService(
            Integer districtId,
            Integer mandalId,
            Integer gpId,
            String serviceCode) {

        if (districtId == null || mandalId == null || gpId == null) {
            throw new IllegalArgumentException(
                    "District, Mandal and Gram Panchayat are required."
            );
        }

        if (!"PROPERTY_TAX".equalsIgnoreCase(serviceCode)) {
            throw new IllegalArgumentException(
                    "This endpoint supports only the PROPERTY_TAX service."
            );
        }

        // Confirm that all three IDs exist before searching.
        if (!districtRepository.existsById(districtId)
                || !mandalRepository.existsById(mandalId)
                || !gramPanchayatRepository.existsById(gpId)) {
            throw new IllegalArgumentException(
                    "Invalid District, Mandal or Gram Panchayat ID."
            );
        }
    }
}