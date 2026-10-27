package com.housekeeping.management.controller;

import com.housekeeping.management.dto.request.PortalForms.HouseForm;
import com.housekeeping.management.domain.HouseAddressType;
import com.housekeeping.management.service.HouseService;
import com.housekeeping.management.web.PortalUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/houses")
public class HouseController {
    private final HouseService houses;

    public HouseController(HouseService houses) {
        this.houses = houses;
    }

    @GetMapping
    String houses(@AuthenticationPrincipal PortalUser user, @RequestParam(required = false) Long editId, Model model) {
        model.addAttribute("houses", houses.findCustomerHouses(user.getUserId()));
        model.addAttribute("addressTypes", HouseAddressType.values());
        if (!model.containsAttribute("houseForm")) {
            if (editId == null) {
                model.addAttribute("houseForm", new HouseForm(user.getDisplayName(), "", "", "", "",
                        HouseAddressType.HOME, false, ""));
            } else {
                var house = houses.findCustomerHouse(user.getUserId(), editId);
                model.addAttribute("houseForm", new HouseForm(house.getRecipientName(), house.getRecipientPhone(),
                        house.getProvinceCity() == null ? "" : house.getProvinceCity(),
                        house.getWardCommune() == null ? "" : house.getWardCommune(),
                        house.getAddressDetail() == null ? house.getAddress() : house.getAddressDetail(),
                        house.getAddressType(), house.isDefaultAddress(), house.getAddressNote()));
                model.addAttribute("editHouseId", editId);
            }
        } else if (editId != null) {
            model.addAttribute("editHouseId", editId);
        }
        return "customer/houses";
    }

    @PostMapping
    String createHouse(@AuthenticationPrincipal PortalUser user, @Valid @ModelAttribute("houseForm") HouseForm form,
            BindingResult errors, Model model) {
        if (errors.hasErrors()) return houses(user, null, model);
        try {
            houses.save(user.getUserId(), null, form);
            return "redirect:/customer/houses?saved";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", failure.getMessage());
            return houses(user, null, model);
        }
    }

    @PostMapping("/{id}")
    String updateHouse(@PathVariable Long id, @AuthenticationPrincipal PortalUser user,
            @Valid @ModelAttribute("houseForm") HouseForm form, BindingResult errors, Model model) {
        if (errors.hasErrors()) return houses(user, id, model);
        try {
            houses.save(user.getUserId(), id, form);
            return "redirect:/customer/houses?saved";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("error", failure.getMessage());
            return houses(user, id, model);
        }
    }

    @PostMapping("/{id}/delete")
    String deleteHouse(@PathVariable Long id, @AuthenticationPrincipal PortalUser user, RedirectAttributes flash) {
        try {
            houses.softDelete(user.getUserId(), id);
            flash.addFlashAttribute("success", "Đã ẩn địa chỉ nhà khỏi danh sách đặt lịch.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            flash.addFlashAttribute("error", failure.getMessage());
        }
        return "redirect:/customer/houses";
    }
}
