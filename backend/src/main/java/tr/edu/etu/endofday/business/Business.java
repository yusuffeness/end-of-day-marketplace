package tr.edu.etu.endofday.business;

/** A row of ISLETME. */
public record Business(
        long id,
        long ownerId,
        String name,
        BusinessType type,
        String city,
        String district,
        String address,
        String phone,
        ApprovalStatus approvalStatus,
        boolean active) {

    /** Only an approved and active business can publish listings and take orders. */
    public boolean isOpenForSale() {
        return approvalStatus == ApprovalStatus.ONAYLANDI && active;
    }

    public boolean isOwnedBy(long userId) {
        return ownerId == userId;
    }
}
