package com.infinite.controller;

import java.io.Serializable;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;

import com.infinite.dao.DoctorAvailabilityDaoImpl;
import com.infinite.model.DoctorAvailability;

@ManagedBean
@ViewScoped
public class DoctorAvailabilityController implements Serializable {

	private static final long serialVersionUID = 1L;

	private DoctorAvailabilityDaoImpl dao = new DoctorAvailabilityDaoImpl();

	private String doctorId = "D1003"; // Hardcoded for demo
	private List<DayAvailabilitySummary> groupedAvailabilityList;
	private List<DoctorAvailability> searchedAvailabilityList;
	private DoctorAvailability selectedAvailability;
	private java.util.Date searchDate;
	private boolean initialized = false;

	public void init() {
		if (initialized)
			return;
		initialized = true;
		loadAllUpcomingAvailability();
	}

	public void loadAllUpcomingAvailability() {
		groupedAvailabilityList = new ArrayList<>();

		Date today = new Date(System.currentTimeMillis());
		List<DoctorAvailability> futureSlots = dao.getUpcomingAvailabilitiesForDoctor(doctorId, today);

		Map<Date, List<DoctorAvailability>> dateMap = new TreeMap<>();

		for (DoctorAvailability slot : futureSlots) {
			Date availableDate = slot.getAvailable_date();
			dateMap.computeIfAbsent(availableDate, k -> new ArrayList<>()).add(slot);
		}
		FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put("dateMap", dateMap);
		for (Map.Entry<Date, List<DoctorAvailability>> entry : dateMap.entrySet()) {
			Date sqlDate = entry.getKey();
			List<DoctorAvailability> dailySlots = entry.getValue();

			String displayDate = formatDisplayDate(sqlDate);

			int totalAvailableSlots = 0;
			String representativeAvailabilityId = null;

			for (DoctorAvailability da : dailySlots) {
				int remaining = dao.getRemainingSlotsForAvailability(da.getAvailability_id());
				totalAvailableSlots += remaining;

				// Pick the first availability_id for the date (for selection purposes)
				if (representativeAvailabilityId == null) {
					representativeAvailabilityId = da.getAvailability_id();
				}
			}

			groupedAvailabilityList.add(
				new DayAvailabilitySummary(sqlDate, displayDate, totalAvailableSlots, representativeAvailabilityId)
			);
		}
	}

	private String formatDisplayDate(Date sqlDate) {
		SimpleDateFormat formatter = new SimpleDateFormat("E d, MMM", Locale.ENGLISH);
		return formatter.format(sqlDate);
	}

	public void searchAvailabilityByDate() {
		searchedAvailabilityList = new ArrayList<>();
		if (searchDate != null) {
			Date sqlDate = new Date(searchDate.getTime());
			searchedAvailabilityList = dao.getAvailableSlotsByDoctorAndDate(doctorId, sqlDate);
		}
	}

	public void selectAvailability(DoctorAvailability availability) {
		this.selectedAvailability = availability;
	}

	// ✅ Getters and Setters
	public String getDoctorId() {
		return doctorId;
	}

	public List<DayAvailabilitySummary> getGroupedAvailabilityList() {
		init();
		return groupedAvailabilityList;
	}

	public List<DoctorAvailability> getSearchedAvailabilityList() {
		return searchedAvailabilityList;
	}

	public java.util.Date getSearchDate() {
		return searchDate;
	}

	public void setSearchDate(java.util.Date searchDate) {
		this.searchDate = searchDate;
	}

	public DoctorAvailability getSelectedAvailability() {
		return selectedAvailability;
	}

	public void setSelectedAvailability(DoctorAvailability selectedAvailability) {
		this.selectedAvailability = selectedAvailability;
	}

	// ✅ Inner class with availabilityId now included
	public static class DayAvailabilitySummary {
		private Date date;
		private String displayDate;
		private int totalSlots;
		private String availabilityId;

		public DayAvailabilitySummary(Date date, String displayDate, int totalSlots, String availabilityId) {
			this.date = date;
			this.displayDate = displayDate;
			this.totalSlots = totalSlots;
			this.availabilityId = availabilityId;
		}

		public Date getDate() {
			return date;
		}

		public String getDisplayDate() {
			return displayDate;
		}

		public int getTotalSlots() {
			return totalSlots;
		}

		public String getAvailabilityId() { // ✅ This is needed in your JSP
			return availabilityId;
		}
	}
}
