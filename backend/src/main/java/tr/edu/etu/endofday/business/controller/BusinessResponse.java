package tr.edu.etu.endofday.business.controller;

import tr.edu.etu.endofday.business.Business;

/** A business as returned to its owner and to administrators. */
public record BusinessResponse(
        long id,
        String name,
        String type,
        String city,
        String district,
        String address,
        String phone,
        String approvalStatus,
        boolean active) {

    public static BusinessResponse from(Business business) {
        return new BusinessResponse(
                business.id(),
                business.name(),
                business.type().name(),
                business.city(),
                business.district(),
                business.address(),
                business.phone(),
                business.approvalStatus().name(),
                business.active());
    }
}
