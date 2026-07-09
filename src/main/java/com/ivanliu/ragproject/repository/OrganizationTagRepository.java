package com.ivanliu.ragproject.repository;

import com.ivanliu.ragproject.model.OrganizationTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
    Optional<OrganizationTag> findByTagId(String tagId);
    List<OrganizationTag> findByParentTag(String parentTag);
    boolean existsByTagId(String tagId);
} 