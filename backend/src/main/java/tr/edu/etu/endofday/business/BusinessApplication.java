package tr.edu.etu.endofday.business;

/** A business with the name of its owner, as the administrator reviews it. */
public record BusinessApplication(Business business, String ownerFirstName, String ownerLastName) {}
