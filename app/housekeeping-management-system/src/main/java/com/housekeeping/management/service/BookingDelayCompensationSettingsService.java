package com.housekeeping.management.service;

import com.housekeeping.management.dto.BookingDelayCompensation.Tier;
import java.util.List;

public interface BookingDelayCompensationSettingsService {

    public List<Tier> get();
    public void save(List<Tier> proposed);}
