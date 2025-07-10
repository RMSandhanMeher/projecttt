package com.infinite.dao;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;

import com.infinite.model.Appointment;
import com.infinite.model.AppointmentStatus;
import com.infinite.model.DoctorAvailability;
import com.infinite.util.SessionHelper;

public class AppointmentDaoImpl implements AppointmentDao {

	// Generates the next appointment ID in APPT### format
	private String generateNextAppointmentId(Session session) {
		String prefix = "APPT";
		String hql = "SELECT a.appointment_id FROM Appointment a ORDER BY a.appointment_id DESC";
		Query query = session.createQuery(hql);
		query.setMaxResults(1);
		String lastId = (String) query.uniqueResult();

		int nextNumber = 101; // default start
		if (lastId != null && lastId.startsWith(prefix)) {
			try {
				int lastNumber = Integer.parseInt(lastId.substring(prefix.length()));
				nextNumber = lastNumber + 1;
			} catch (NumberFormatException e) {
				// fallback to default 101
			}
		}

		return prefix + nextNumber;
	}

	@Override
	public String bookAnAppointment(Appointment appointment) {
		Transaction tx = null;
		String result = null;

		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			String availabilityId = appointment.getAvailability().getAvailability_id();
			String recipientId = appointment.getRecipient().getH_id();
			int slotNo = appointment.getSlot_no();
			Timestamp now = new Timestamp(System.currentTimeMillis());

			// 1. Prevent booking in the past
			Timestamp slotStart = appointment.getStart();
			if (slotStart == null || slotStart.before(now)) {
				return "Cannot book an appointment in the past.";
			}

			// 2. Check if recipient already has overlapping appointment
			Query overlapQuery = session.createQuery(
					"FROM Appointment a WHERE a.recipient.h_id = :recipientId AND a.status IN ('BOOKED', 'PENDING') "
							+ "AND (a.start <= :endTime AND a.end >= :startTime)");
			overlapQuery.setParameter("recipientId", recipientId);
			overlapQuery.setParameter("startTime", appointment.getStart());
			overlapQuery.setParameter("endTime", appointment.getEnd());

			if (!overlapQuery.list().isEmpty()) {
				return "Recipient already has an overlapping appointment during this time.";
			}

			// 3. Check if the slot number is already booked in this availability
			Query slotQuery = session
					.createQuery("FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND slot_no = :slotNo AND status IN ('BOOKED', 'PENDING')");
			slotQuery.setParameter("availabilityId", availabilityId);
			slotQuery.setParameter("slotNo", slotNo);

			if (!slotQuery.list().isEmpty()) {
				return "Slot number " + slotNo + " is already booked for this availability.";
			}

			// 4. Check if max capacity is reached for this availability
			Query bookedCountQuery = session.createQuery(
					"SELECT COUNT(*) FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND status IN ('BOOKED', 'PENDING')");
			bookedCountQuery.setParameter("availabilityId", availabilityId);
			long bookedCount = (Long) bookedCountQuery.uniqueResult();

			int maxCapacity = appointment.getAvailability().getMax_capacity();
			if (bookedCount >= maxCapacity) {
				return "All slots for this availability are already full.";
			}

			// 5. Check if recipient already has 10 upcoming appointments
			Query upcomingQuery = session
					.createQuery("SELECT COUNT(*) FROM Appointment WHERE recipient.h_id = :recipientId "
							+ "AND status IN ('BOOKED', 'PENDING') AND start > :now");
			upcomingQuery.setParameter("recipientId", recipientId);
			upcomingQuery.setParameter("now", now);

			long upcomingCount = (Long) upcomingQuery.uniqueResult();
			if (upcomingCount >= 10) {
				return "Recipient already has 10 upcoming appointments.";
			}

			// 6. Save the appointment
			appointment.setRequested_at(now);
			appointment.setBooked_at(now);
			appointment.setStatus(AppointmentStatus.BOOKED);
			appointment.setAppointment_id(generateNextAppointmentId(session));

			session.save(appointment);
			tx.commit();

			// Placeholder: Send email, generate PDF, send OTP
			// EmailService.sendAppointmentEmail(appointment);
			// PdfService.generateAppointmentSlip(appointment);
			// OtpService.sendOtpToRecipient(appointment.getRecipient().getMobile());

			result = "Appointment booked successfully with ID: " + appointment.getAppointment_id();
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			e.printStackTrace();
			result = "Error booking appointment: " + e.getMessage();
		}

		return result;
	}

	@Override
	public boolean isAvailabilitySlotFull(String availabilityId) {
		try {

			Session session = SessionHelper.getSessionFactory().openSession();
			// Step 1: Get total booked/pending appointments for the availability
			Query countQuery = session.createQuery(
					"SELECT COUNT(*) FROM Appointment WHERE availability.availability_id = :availabilityId "
							+ "AND status IN ('BOOKED', 'PENDING')");
			countQuery.setParameter("availabilityId", availabilityId);
			long bookedCount = (Long) countQuery.uniqueResult();

			// Step 2: Get max capacity from DoctorAvailability
			Query capacityQuery = session.createQuery(
					"SELECT a.max_capacity FROM DoctorAvailability a WHERE a.availability_id = :availabilityId");
			capacityQuery.setParameter("availabilityId", availabilityId);
			Integer maxCapacity = (Integer) capacityQuery.uniqueResult();

			if (maxCapacity == null) {
				return false; // availability not found, assume not full
			}

			return bookedCount >= maxCapacity;
		} catch (Exception e) {
			e.printStackTrace();
			return false; // on error, assume not full (or log as needed)
		}
	}

	@Override
	public List<Appointment> getUpcomingAppointmentsByRecipient(String recipientId) {
		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			Timestamp now = new Timestamp(System.currentTimeMillis());

			Query query = session.createQuery("FROM Appointment a WHERE a.recipient.h_id = :recipientId "
					+ "AND a.status IN ('BOOKED', 'PENDING') " + "AND a.start > :now ORDER BY a.start ASC");
			query.setParameter("recipientId", recipientId);
			query.setParameter("now", now);
			query.setMaxResults(10); // Only next 10 appointments allowed

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();

			return list;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public List<Appointment> getPastAppointmentsByRecipient(String recipientId) {
		try {

			Session session = SessionHelper.getSessionFactory().openSession();
			Timestamp now = new Timestamp(System.currentTimeMillis());

			Query query = session.createQuery("FROM Appointment a WHERE a.recipient.h_id = :recipientId "
					+ "AND a.start < :now ORDER BY a.start DESC");
			query.setParameter("recipientId", recipientId);
			query.setParameter("now", now);

			@SuppressWarnings("unchecked")
			List<Appointment> list = query.list();

			return list;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Appointment getAppointmentById(String appointmentId) {
		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			return (Appointment) session.get(Appointment.class, appointmentId);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean cancelAppointment(String appointmentId) {
		Transaction tx = null;
		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			Appointment appointment = (Appointment) session.get(Appointment.class, appointmentId);
			if (appointment == null) {
				return false; // No such appointment
			}

			// Check if appointment is in the future
			Timestamp now = new Timestamp(System.currentTimeMillis());
			if (appointment.getStart() != null && appointment.getStart().before(now)) {
				return false; // Past appointment can't be cancelled
			}

			appointment.setStatus(AppointmentStatus.CANCELLED);
			appointment.setCancelled_at(now);

			session.update(appointment);
			tx.commit();
			return true;
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean updateAppointment(Appointment updatedAppointment) {
		Transaction tx = null;
		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Load the original appointment
			Appointment existing = (Appointment) session.get(Appointment.class, updatedAppointment.getAppointment_id());
			if (existing == null)
				return false;

			// Allow update only if appointment is in the future
			Timestamp now = new Timestamp(System.currentTimeMillis());
			if (existing.getStart() != null && existing.getStart().before(now)) {
				return false; // Cannot update past appointment
			}

			String availabilityId = updatedAppointment.getAvailability().getAvailability_id();
			String recipientId = updatedAppointment.getRecipient().getH_id();
			int slotNo = updatedAppointment.getSlot_no();

			// Check if new slot overlaps with another existing appointment of recipient
			Query overlapQuery = session.createQuery(
					"FROM Appointment a WHERE a.recipient.h_id = :recipientId AND a.status IN ('BOOKED', 'PENDING') "
							+ "AND ((a.start <= :endTime AND a.end >= :startTime)) AND a.appointment_id != :currentId");
			overlapQuery.setParameter("recipientId", recipientId);
			overlapQuery.setParameter("startTime", updatedAppointment.getStart());
			overlapQuery.setParameter("endTime", updatedAppointment.getEnd());
			overlapQuery.setParameter("currentId", updatedAppointment.getAppointment_id());

			if (!overlapQuery.list().isEmpty()) {
				return false; // Overlapping found
			}

			// Check if the new slot number is already taken in same availability
			Query slotQuery = session.createQuery(
					"FROM Appointment a WHERE a.availability.availability_id = :availabilityId AND a.slot_no = :slotNo "
							+ "AND a.status IN ('BOOKED', 'PENDING') AND a.appointment_id != :currentId");
			slotQuery.setParameter("availabilityId", availabilityId);
			slotQuery.setParameter("slotNo", slotNo);
			slotQuery.setParameter("currentId", updatedAppointment.getAppointment_id());

			if (!slotQuery.list().isEmpty()) {
				return false; // Slot taken
			}

			// Update details
			existing.setAvailability(updatedAppointment.getAvailability());
			existing.setSlot_no(slotNo);
			existing.setStart(updatedAppointment.getStart());
			existing.setEnd(updatedAppointment.getEnd());
			existing.setNotes(updatedAppointment.getNotes());

			session.update(existing);
			tx.commit();
			return true;
		} catch (Exception e) {
			if (tx != null)
				tx.rollback();
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public int getBookedCountForAvailability(String availabilityId) {
		int count = 0;
		try {
			Session session = SessionHelper.getSessionFactory().openSession();
			Query query = session.createQuery(
					"SELECT COUNT(*) FROM Appointment a " + "WHERE a.availability.availability_id = :availabilityId "
							+ "AND a.status IN ('BOOKED', 'PENDING')");
			query.setParameter("availabilityId", availabilityId);
			Long result = (Long) query.uniqueResult();
			count = result != null ? result.intValue() : 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return count;
	}

	@Override
	public boolean hasOverlappingAppointment(String recipientId, String availabilityId, Timestamp start,
			Timestamp end) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Integer> getAvailableSlotNumbers(String availabilityId) {
	    List<Integer> availableSlots = new ArrayList<>();
	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        // Step 1: Get max_capacity from DoctorAvailability
	        Query capacityQuery = session.createQuery(
	            "SELECT da.max_capacity FROM DoctorAvailability da WHERE da.availability_id = :availabilityId"
	        );
	        capacityQuery.setParameter("availabilityId", availabilityId);
	        Integer maxCapacity = (Integer) capacityQuery.uniqueResult();

	        if (maxCapacity == null || maxCapacity <= 0) {
	            return availableSlots; // return empty list if invalid
	        }

	        // Step 2: Get all booked slot numbers
	        Query bookedQuery = session.createQuery(
	            "SELECT a.slot_no FROM Appointment a " +
	            "WHERE a.availability.availability_id = :availabilityId " +
	            "AND a.status IN ('BOOKED', 'PENDING')"
	        );
	        bookedQuery.setParameter("availabilityId", availabilityId);
	        List<Integer> bookedSlots = bookedQuery.list();

	        // Step 3: Prepare the full range and subtract booked slots
	        for (int i = 1; i <= maxCapacity; i++) {
	            if (!bookedSlots.contains(i)) {
	                availableSlots.add(i);
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return availableSlots;
	}


	@Override
	public boolean isSlotAlreadyBooked(String availabilityId, int slotNo) {
	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        Query query = session.createQuery(
	            "SELECT count(*) FROM Appointment a " +
	            "WHERE a.availability.availability_id = :availabilityId " +
	            "AND a.slot_no = :slotNo AND a.status IN ('BOOKED', 'PENDING')"
	        );
	        query.setParameter("availabilityId", availabilityId);
	        query.setParameter("slotNo", slotNo);

	        Long count = (Long) query.uniqueResult();
	        return count != null && count > 0;
	    } catch (Exception e) {
	        e.printStackTrace();
	        return false; // return false on error to avoid false positives
	    }
	}


	@Override
	public List<Appointment> getAppointmentsByAvailability(String availabilityId) {
	    List<Appointment> appointments = null;
	    
	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        Query query = session.createQuery(
	            "FROM Appointment a WHERE a.availability.availability_id = :availabilityId ORDER BY a.slot_no ASC"
	        );
	        query.setParameter("availabilityId", availabilityId);

	        appointments = query.list();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return appointments;
	}


	@Override
	public boolean isAppointmentInPast(String appointmentId) {
	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        Appointment appointment = (Appointment) session.get(Appointment.class, appointmentId);

	        if (appointment != null && appointment.getStart() != null) {
	            Timestamp now = new Timestamp(System.currentTimeMillis());
	            return appointment.getStart().before(now);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return false;
	}

	@Override
	public List<Appointment> getAppointmentsByDoctorAndDate(String doctorId, Date date) {
	    List<Appointment> appointments = null;

	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        Query query = session.createQuery(
	            "FROM Appointment a WHERE a.doctor.doctor_id = :doctorId AND DATE(a.start) = :appointmentDate"
	        );
	        query.setParameter("doctorId", doctorId);
	        query.setParameter("appointmentDate", date);

	        appointments = query.list();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return appointments;
	}

	@Override
	public boolean isSlotTimeInFuture(String availabilityId, int slotNo) {
	    try  {
	    	Session session = SessionHelper.getSessionFactory().openSession();
	        DoctorAvailability availability = (DoctorAvailability) session.get(DoctorAvailability.class, availabilityId);
	        if (availability == null) return false;

	        // Calculate slot duration
	        int windowMinutes = availability.getPatient_window(); // e.g., 15
	        if (windowMinutes <= 0) return false;

	        // Calculate slot start time
	        java.sql.Time startTime = availability.getStart_time();
	        Timestamp availableDateTime = Timestamp.valueOf(availability.getAvailable_date().toString() + " " + startTime.toString());

	        // Calculate the start time of the specific slot
	        long slotStartMillis = availableDateTime.getTime() + (slotNo - 1) * windowMinutes * 60 * 1000L;
	        Timestamp slotStart = new Timestamp(slotStartMillis);

	        // Compare with current time
	        return slotStart.after(new Timestamp(System.currentTimeMillis()));
	    } catch (Exception e) {
	        e.printStackTrace();
	        return false;
	    }
	}


}
