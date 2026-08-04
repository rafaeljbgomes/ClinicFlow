package com.clinicflow.auth.application.ports;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;

import java.util.Optional;

public interface UserRepository {
    boolean existsByEmail(EmailAddress email);
    void save(User user);
    Optional<User> findByEmail(EmailAddress email);
    Optional<User> findById(UserId id);
}
