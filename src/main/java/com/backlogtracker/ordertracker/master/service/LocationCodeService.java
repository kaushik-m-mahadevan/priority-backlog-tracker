package com.backlogtracker.ordertracker.master.service;

import org.springframework.stereotype.Service;

import com.backlogtracker.ordertracker.OrderCodeWidths;
import com.backlogtracker.ordertracker.master.domain.LocationCode;
import com.backlogtracker.ordertracker.master.repository.LocationCodeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationCodeService {

    private final LocationCodeRepository repository;
    private final RandomCodeAssigner codeAssigner;

    /** The group's existing code for this location name, or a freshly assigned one. */
    public LocationCode getOrCreate(String groupId, String locationName) {
        return repository.findByGroupIdAndLocationNameIgnoreCase(groupId, locationName)
                .orElseGet(() -> codeAssigner.assign(OrderCodeWidths.LOCATION_CODE_DIGITS, code -> repository.save(LocationCode.builder()
                        .groupId(groupId)
                        .locationName(locationName)
                        .code(code)
                        .build())));
    }
}
