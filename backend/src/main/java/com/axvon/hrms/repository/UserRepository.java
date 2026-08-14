package com.axvon.hrms.repository;
import com.axvon.hrms.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface UserRepository extends JpaRepository<User,Long>{
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
