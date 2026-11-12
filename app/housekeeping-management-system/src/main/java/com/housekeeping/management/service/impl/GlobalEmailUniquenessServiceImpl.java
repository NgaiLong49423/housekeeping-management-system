package com.housekeeping.management.service.impl;

import com.housekeeping.management.repository.UserRepository;
import com.housekeeping.management.service.DuplicateEmailException;
import com.housekeeping.management.service.GlobalEmailUniquenessService;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalEmailUniquenessServiceImpl implements GlobalEmailUniquenessService {
    private final UserRepository users;

    public GlobalEmailUniquenessServiceImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public <T> T executeWithUniqueEmail(String submittedEmail, Function<String, T> persistCanonicalEmail) {
        String canonicalEmail = GlobalEmailUniquenessService.canonicalize(submittedEmail);
        if (users.findByEmailForUpdate(canonicalEmail).isPresent()) {
            throw new DuplicateEmailException(canonicalEmail);
        }
        return persistCanonicalEmail.apply(canonicalEmail);
    }
}
