package com.infinite.main;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.infinite.dao.AppointmentDao;
import com.infinite.dao.AppointmentDaoImpl;
import com.infinite.model.Appointment;
import com.infinite.model.AppointmentStatus;
import com.infinite.model.DoctorAvailability;
import com.infinite.model.Doctors;
import com.infinite.model.Providers;
import com.infinite.model.Recipient;

public class Appoint {

	public static void main(String[] args) {

		// Initialize DAO
		AppointmentDao dao = new AppointmentDaoImpl();

		// ========== 1. Book a new appointment ==========
		Appointment appointment = new Appointment();
		String apptId = "APPT" + System.currentTimeMillis(); // simple ID generator
		appointment.setAppointment_id(apptId);

		Doctors doctor = new Doctors();
		doctor.setDoctor_id("D1003");

		Providers provider = new Providers();
		provider.setProvider_id("P1003");

		Recipient recipient = new Recipient();
		recipient.setH_id("H1003");

		DoctorAvailability availability = new DoctorAvailability();
		availability.setAvailability_id("A2012");
		availability.setMax_capacity(4);

		appointment.setDoctor(doctor);
		appointment.setProvider(provider);
		appointment.setRecipient(recipient);
		appointment.setAvailability(availability);
		appointment.setSlot_no(2);

		Timestamp availabilityStart = Timestamp.valueOf("2025-07-09 10:30:00");
		Timestamp availabilityEnd = Timestamp.valueOf("2025-07-09 11:30:00");
		int capacity = availability.getMax_capacity();

		long window = (availabilityEnd.getTime() - availabilityStart.getTime()) / (1000 * 60) / capacity;
		long slotStart = availabilityStart.getTime() + (appointment.getSlot_no() - 1) * window * 60 * 1000;
		long slotEnd = slotStart + window * 60 * 1000;

		appointment.setStart(new Timestamp(slotStart));
		appointment.setEnd(new Timestamp(slotEnd));
		appointment.setRequested_at(Timestamp.valueOf(LocalDateTime.now()));
		appointment.setBooked_at(Timestamp.valueOf(LocalDateTime.now()));
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setNotes("Created from test main");

		String result = dao.bookAnAppointment(appointment);
		System.out.println(result);

		// ========== 2. Get Upcoming Appointments ==========
		List<Appointment> upcoming = dao.getUpcomingAppointmentsByRecipient("H1003");
		System.out.println("\nUpcoming Appointments:");
		for (Appointment a : upcoming) {
			System.out.println("• " + a.getAppointment_id() + " (" + a.getStart() + ")");
		}

		// ========== 3. Get Past Appointments ==========
		List<Appointment> past = dao.getPastAppointmentsByRecipient("H1003");
		System.out.println("\nPast Appointments:");
		for (Appointment a : past) {
			System.out.println("• " + a.getAppointment_id() + " (" + a.getStart() + ")");
		}

		// ========== 4. Get Appointment by ID ==========
		Appointment fetched = dao.getAppointmentById(apptId);
		System.out.println("\nFetched Appointment by ID:");
		if (fetched != null) {
			System.out.println("→ ID: " + fetched.getAppointment_id());
			System.out.println("→ Slot: " + fetched.getSlot_no());
			System.out.println("→ Status: " + fetched.getStatus());
		} else {
			System.out.println("No appointment found with ID: " + apptId);
		}

		// ========== 5. Get Slot Booking Count ==========
		int count = dao.getBookedCountForAvailability("A2012");
		System.out.println("\nTotal bookings for A2012: " + count);

		// ========== 6. Get Available Slot Numbers ==========
		List<Integer> availableSlots = dao.getAvailableSlotNumbers("A2012");
		System.out.println("\nAvailable Slots for A2012:");
		for (int s : availableSlots) {
			System.out.println("• Slot " + s);
		}

		// ========== 7. Check if Slot Already Booked ==========
		boolean booked = dao.isSlotAlreadyBooked("A2012", 2);
		System.out.println("\nSlot 2 booked? " + (booked ? "Yes" : "No"));

		// ========== 8. Cancel Appointment ==========
		boolean cancelled = dao.cancelAppointment(apptId);
		System.out.println("\nCancel Appointment " + apptId + ": " + (cancelled ? "Success" : "Failed"));

		// ========== 9. Recheck Status After Cancellation ==========
		Appointment afterCancel = dao.getAppointmentById(apptId);
		if (afterCancel != null) {
			System.out.println("Status after cancel: " + afterCancel.getStatus());
		}
	}
}
