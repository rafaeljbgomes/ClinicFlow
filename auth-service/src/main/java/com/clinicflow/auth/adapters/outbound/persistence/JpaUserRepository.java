package com.clinicflow.auth.adapters.outbound.persistence;

import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserJpaRepository delegate;
    private final UserPersistenceMapper mapper;

    public JpaUserRepository(SpringDataUserJpaRepository delegate, UserPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return delegate.existsByEmail(email.value());
    }

    @Override
    public void save(User user) {
        delegate.save(mapper.toJpa(user));
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return delegate.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }
}
