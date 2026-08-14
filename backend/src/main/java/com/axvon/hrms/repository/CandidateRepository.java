package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CandidateRepository extends JpaRepository<Candidate,Long>{}
