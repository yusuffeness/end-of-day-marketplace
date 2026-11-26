package tr.edu.etu.endofday.business.controller;

/** Turkish validation messages of the business fields. */
final class BusinessRules {

    static final String NAME_REQUIRED = "İşletme adı zorunludur.";
    static final String NAME_TOO_LONG = "İşletme adı en fazla 100 karakter olabilir.";
    static final String CITY_REQUIRED = "Şehir zorunludur.";
    static final String CITY_TOO_LONG = "Şehir en fazla 50 karakter olabilir.";
    static final String DISTRICT_REQUIRED = "İlçe zorunludur.";
    static final String DISTRICT_TOO_LONG = "İlçe en fazla 50 karakter olabilir.";
    static final String ADDRESS_REQUIRED = "Adres zorunludur.";
    static final String ADDRESS_TOO_LONG = "Adres en fazla 255 karakter olabilir.";
    static final String PHONE_REQUIRED = "Telefon zorunludur.";

    private BusinessRules() {}
}
