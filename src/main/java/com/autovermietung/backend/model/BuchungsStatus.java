package com.autovermietung.backend.model;

public enum BuchungsStatus {
    RESERVIERT,  // Slot ist blockiert, aber noch nicht bezahlt
    BEZAHLT,     // Zahlung erfolgt, Buchung fix
    STORNIERT}

