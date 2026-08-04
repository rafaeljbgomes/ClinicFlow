package com.clinicflow.auth.application;

import com.clinicflow.auth.application.exceptions.UserNotFoundException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.application.queries.GetUserQuery;
import com.clinicflow.auth.application.results.UserResult;
import org.springframework.stereotype.Service;

@Service
public class GetUserService {
    private final UserRepository userRepository;
    private final UserApplicationMapper mapper;

    public GetUserService(UserRepository userRepository, UserApplicationMapper mapper) {
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public UserResult get(GetUserQuery query) {
        return userRepository.findById(query.userId()).map(mapper::toResult)
                .orElseThrow(UserNotFoundException::new);
    }
}
