/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2012  Linagora
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version, provided you comply 
 * with the Additional Terms applicable for OBM connector by Linagora 
 * pursuant to Section 7 of the GNU Affero General Public License, 
 * subsections (b), (c), and (e), pursuant to which you must notably (i) retain 
 * the “Message sent thanks to OBM, Free Communication by Linagora” 
 * signature notice appended to any and all outbound messages 
 * (notably e-mail and meeting requests), (ii) retain all hypertext links between 
 * OBM and obm.org, as well as between Linagora and linagora.com, and (iii) refrain 
 * from infringing Linagora intellectual property rights over its trademarks 
 * and commercial brands. Other Additional Terms apply, 
 * see <http://www.linagora.com/licenses/> for more details. 
 *
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details. 
 *
 * You should have received a copy of the GNU Affero General Public License 
 * and its applicable Additional Terms for OBM along with this program. If not, 
 * see <http://www.gnu.org/licenses/> for the GNU Affero General Public License version 3 
 * and <http://www.linagora.com/licenses/> for the Additional Terms applicable to 
 * OBM connectors. 
 * 
 * ***** END LICENSE BLOCK ***** */
package org.obm.push.service.impl;

import org.apache.commons.codec.binary.Hex;
import org.obm.push.bean.BackendSession;
import org.obm.push.bean.Device;
import org.obm.push.bean.MSEvent;
import org.obm.push.bean.MSEventUid;
import org.obm.push.calendar.EventConverter;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.ConversionException;
import org.obm.push.service.EventService;
import org.obm.push.store.CalendarDao;
import org.obm.sync.calendar.Event;
import org.obm.sync.calendar.EventExtId;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class EventServiceImpl implements EventService {

	private final CalendarDao calendarDao;
	private final EventConverter eventConverter;
	
	@Inject
	private EventServiceImpl(CalendarDao calendarDao, EventConverter eventConverter) {
		super();
		this.calendarDao = calendarDao;
		this.eventConverter = eventConverter;
	}

	@Override
	public MSEvent convertEventToMSEvent(BackendSession bs, Event event) throws DaoException, ConversionException {
		MSEventUid msEventUid = getMSEventUidFor(event, bs.getDevice());
		MSEvent msEvent = eventConverter.convert(event, msEventUid, bs.getCredentials().getUser());
		return msEvent;
	}
	
	private MSEventUid getMSEventUidFor(Event event, Device device) throws DaoException {
		Preconditions.checkNotNull(event.getExtId(), "Event must contain an extId");
		MSEventUid msEventUidFromDatabase = retrieveMSEventUidFromDatabase(event, device);
		if (msEventUidFromDatabase != null) {
			return msEventUidFromDatabase;
		}
		return createMSEventUidInDatabase(event, device);
	}

	private MSEventUid createMSEventUidInDatabase(Event event, Device device) throws DaoException {
		MSEventUid convertedFromExtId = createMSEventUidFromEventExtId(event);
		calendarDao.insertExtIdMSEventUidMapping(event.getExtId(), convertedFromExtId, device);
		return convertedFromExtId;
	}

	private MSEventUid retrieveMSEventUidFromDatabase(Event event, Device device)
			throws DaoException {
		MSEventUid msEventUidFromDatabase = calendarDao.getMSEventUidFor(event.getExtId(), device);
		return msEventUidFromDatabase;
	}

	private MSEventUid createMSEventUidFromEventExtId(Event event) {
		return new MSEventUid(convertExtIdAsHex(event.getExtId()));
	}
	
	private String convertExtIdAsHex(EventExtId extId) {
		return Hex.encodeHexString(extId.getExtId().getBytes(Charsets.US_ASCII));
	}

	@Override
	public EventExtId getEventExtIdFor(MSEventUid msEventUid, Device device) throws DaoException {
		return calendarDao.getEventExtIdFor(msEventUid, device);
	}
	
	@Override
	public void trackEventExtIdMSEventUidTranslation(EventExtId eventExtId,
			MSEventUid msEventUid, Device device) throws DaoException {
		calendarDao.insertExtIdMSEventUidMapping(eventExtId, msEventUid, device);
	}
}
