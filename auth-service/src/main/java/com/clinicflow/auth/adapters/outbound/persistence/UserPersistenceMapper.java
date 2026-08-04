package com.clinicflow.auth.adapters.outbound.persistence;

import com.clinicflow.auth.application.mapping.MapperConfiguration;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class UserPersistenceMapper {
    public abstract String copy(String value);

    public JpaUserEntity toJpa(User user) {
        return new JpaUserEntity(
                user.id().value(),
                user.email().value(),
                user.passwordHash().value(),
                user.role(),
                user.fullName().value(),
                user.enabled(),
                user.createdAt()
        );
    }

    public User toDomain(JpaUserEntity entity) {
        return User.rehydrate(
                new UserId(entity.id()),
                new EmailAddress(entity.email()),
                new PasswordHash(entity.passwordHash()),
                entity.role(),
                new FullName(entity.fullName()),
                entity.enabled(),
                entity.createdAt()
        );
    }
}
