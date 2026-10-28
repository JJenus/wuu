package com.surf.tracker.userauth.application.service;

import com.surf.tracker.shared.exception.BusinessRuleException;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.userauth.domain.entity.Organization;
import com.surf.tracker.userauth.infrastructure.repository.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class OrganizationService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$");

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Organization create(String name, String slug) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("ORG_NAME_REQUIRED", "name is required");
        }
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new ValidationException("ORG_SLUG_INVALID", "slug must be 3-100 lowercase alphanumeric + hyphens");
        }
        if (organizationRepository.existsBySlug(slug)) {
            throw new ValidationException("ORG_SLUG_TAKEN", "slug already in use");
        }
        Organization org = Organization.create(name, slug);
        return organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public Organization getById(Long id) {
        return organizationRepository.findById(id)
            .orElseThrow(() -> new BusinessRuleException("ORG_NOT_FOUND", "organization not found"));
    }

    @Transactional(readOnly = true)
    public Organization getBySlug(String slug) {
        return organizationRepository.findBySlug(slug)
            .orElseThrow(() -> new BusinessRuleException("ORG_NOT_FOUND", "organization not found"));
    }

    @Transactional(readOnly = true)
    public List<Organization> listAll() {
        List<Organization> result = new ArrayList<>();
        findAllBatched(PageRequest.of(0, 500), result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Organization> listAllPaged(Pageable pageable) {
        return organizationRepository.findAll(pageable);
    }

    @Transactional
    public Organization update(Long id, String name, String slug) {
        Organization org = getById(id);
        if (organizationRepository.existsBySlug(slug) && !slug.equals(org.getSlug())) {
            throw new ValidationException("ORG_SLUG_TAKEN", "slug already in use");
        }
        org.rename(name);
        org.changeSlug(slug);
        return organizationRepository.save(org);
    }

    @Transactional
    public void delete(Long id) {
        Organization org = getById(id);
        organizationRepository.delete(org);
    }

    private void findAllBatched(Pageable pageable, Consumer<Organization> consumer) {
        Page<Organization> page;
        do {
            page = organizationRepository.findAll(pageable);
            page.forEach(consumer);
            pageable = PageRequest.of(pageable.getPageNumber() + 1, pageable.getPageSize());
        } while (page.hasNext());
    }
}
