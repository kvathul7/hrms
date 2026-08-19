package com.axvon.hrms.service;
import com.axvon.hrms.entity.CompanySettings;
import com.axvon.hrms.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// There is exactly one settings row, id=1. Created on first read so an upgraded
// installation picks up defaults without a migration step.
@Service @RequiredArgsConstructor
public class SettingsService {
    public static final Long ID = 1L;
    private final CompanySettingsRepository repo;

    public CompanySettings get(){
        return repo.findById(ID).orElseGet(() -> repo.save(CompanySettings.builder().id(ID).build()));
    }
}
