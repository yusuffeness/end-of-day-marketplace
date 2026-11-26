package tr.edu.etu.endofday.order;

/** A SIPARIS row read with an exclusive lock. */
public record LockedOrder(long id, long customerId, long businessId, OrderStatus status) {}
