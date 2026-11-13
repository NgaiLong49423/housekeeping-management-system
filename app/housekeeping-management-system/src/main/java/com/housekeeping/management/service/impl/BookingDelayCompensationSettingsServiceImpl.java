package com.housekeeping.management.service.impl;

import com.housekeeping.management.service.BookingDelayCompensationSettingsService;

import com.housekeeping.management.dto.BookingDelayCompensation.Tier;
import com.housekeeping.management.repository.BookingDelayCompensationRepository;
import com.housekeeping.management.entity.BookingDelayCompensationTier;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingDelayCompensationSettingsServiceImpl implements BookingDelayCompensationSettingsService {
    private final BookingDelayCompensationRepository tiers;

    public BookingDelayCompensationSettingsServiceImpl(BookingDelayCompensationRepository tiers) {
        this.tiers = tiers;
    }

    public List<Tier> get() {
        return tiers.findAllByOrderByMinDelayMinutesAsc().stream()
                .map(tier -> new Tier(tier.getMinDelayMinutes(), tier.getMaxDelayMinutes(), Math.min(30, tier.getDiscountPercent())))
                .toList();
    }

    @Transactional
    public void save(List<Tier> proposed) {
        if (proposed == null || proposed.isEmpty()) {
            throw new IllegalArgumentException("Cần cấu hình ít nhất một mức giảm.");
        }
        List<Tier> ordered = proposed.stream().sorted(java.util.Comparator.comparingInt(Tier::minDelayMinutes)).toList();
        if (ordered.getFirst().minDelayMinutes() != 1) {
            throw new IllegalArgumentException("Mức giảm đầu tiên phải bắt đầu từ phút trễ thứ nhất.");
        }
        for (int i = 0; i < ordered.size(); i++) {
            Tier tier = ordered.get(i);
            boolean last = i == ordered.size() - 1;
            if (tier.minDelayMinutes() < 1 || tier.discountPercent() < 0 || tier.discountPercent() > 30) {
                throw new IllegalArgumentException("Số phút phải từ 1 trở lên và phần trăm giảm từ 0 đến 30.");
            }
            if (last) {
                if (tier.maxDelayMinutes() != null) {
                    throw new IllegalArgumentException("Mức giảm cuối phải áp dụng không giới hạn số phút trễ.");
                }
            } else {
                Tier next = ordered.get(i + 1);
                if (tier.maxDelayMinutes() == null || tier.maxDelayMinutes() < tier.minDelayMinutes()
                        || next.minDelayMinutes() != tier.maxDelayMinutes() + 1) {
                    throw new IllegalArgumentException("Các khoảng phút phải liên tiếp, không chồng lấn và không để trống.");
                }
            }
        }
        tiers.deleteAllInBatch();
        tiers.saveAll(ordered.stream().map(tier -> {
            BookingDelayCompensationTier entity = new BookingDelayCompensationTier();
            entity.setMinDelayMinutes(tier.minDelayMinutes());
            entity.setMaxDelayMinutes(tier.maxDelayMinutes());
            entity.setDiscountPercent(tier.discountPercent());
            return entity;
        }).toList());
    }
}
