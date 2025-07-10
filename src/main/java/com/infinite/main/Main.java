package com.infinite.main;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import com.infinite.dao.DoctorAvailabilityDao;
import com.infinite.dao.DoctorAvailabilityDaoImpl;
import com.infinite.model.DoctorAvailability;

public class Main {

    public static void main(String[] args) {
        DoctorAvailabilityDao dao = new DoctorAvailabilityDaoImpl();

        String doctorId = "D1003";
        String providerId = "P1003";
        Date today = Date.valueOf(LocalDate.now());

        // 1. Test getAvailableSlotsByDoctorAndDate
        List<DoctorAvailability> todaySlots = dao.getAvailableSlotsByDoctorAndDate(doctorId, today);
        System.out.println("Available Slots for Doctor " + doctorId + " on " + today + ": " + todaySlots.size());
        for (DoctorAvailability slot : todaySlots) {
            System.out.println(slot);
        }

        // 2. Test getUpcomingAvailabilitiesForDoctor
        List<DoctorAvailability> upcoming = dao.getUpcomingAvailabilitiesForDoctor(doctorId, today);
        System.out.println("\nUpcoming Slots:");
        for (DoctorAvailability a : upcoming) {
            System.out.println(a);
        }

        // 3. Test getAllAvailabilitiesByProvider
        List<DoctorAvailability> providerAvail = dao.getAllAvailabilitiesByProvider(providerId);
        System.out.println("\nAll Availabilities under Provider " + providerId + ":");
        for (DoctorAvailability a : providerAvail) {
            System.out.println(a);
        }

        // 4. Test getTodayAvailabilities
        List<DoctorAvailability> todays = dao.getTodayAvailabilities();
        System.out.println("\nToday’s Availabilities:");
        for (DoctorAvailability a : todays) {
            System.out.println(a);
        }

        // 5. Test getRemainingSlotsForAvailability
        String availabilityId = "A2001";
        int remaining = dao.getRemainingSlotsForAvailability(availabilityId);
        System.out.println("\nRemaining Slots for " + availabilityId + ": " + remaining);

        // 6. Test getAvailabilityById
        DoctorAvailability availability = dao.getAvailabilityById(availabilityId);
        System.out.println("\nAvailability with ID " + availabilityId + ": " + availability);

        // 7. Test getAvailabilityByDoctorAndDate
        List<DoctorAvailability> checkDup = dao.getAvailabilityByDoctorAndDate(doctorId, today);
        System.out.println("\nDuplicate Availability Check: " + checkDup.size());

        // 8. Test deleteAvailabilityIfNoAppointments (Should return false if appointments exist)
        boolean deleted = dao.deleteAvailabilityIfNoAppointments(availabilityId);
        System.out.println("\nDelete Attempt for " + availabilityId + ": " + deleted);
    }
}
