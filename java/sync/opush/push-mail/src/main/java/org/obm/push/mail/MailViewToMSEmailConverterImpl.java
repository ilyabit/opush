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
package org.obm.push.mail;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Set;

import org.minig.imap.Flag;
import org.obm.icalendar.ICalendar;
import org.obm.icalendar.ical4jwrapper.ICalendarEvent;
import org.obm.mail.conversation.EmailView;
import org.obm.mail.conversation.EmailViewAttachment;
import org.obm.mail.conversation.EmailViewInvitationType;
import org.obm.push.bean.MSAttachement;
import org.obm.push.bean.MSEmailBodyType;
import org.obm.push.bean.MSEmailHeader;
import org.obm.push.bean.MSEventUid;
import org.obm.push.bean.MSMessageClass;
import org.obm.push.bean.MethodAttachment;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.ms.MSEmail;
import org.obm.push.bean.ms.MSEmail.MSEmailBuilder;
import org.obm.push.bean.ms.MSEmailBody;
import org.obm.push.bean.msmeetingrequest.MSMeetingRequest;
import org.obm.push.exception.DaoException;
import org.obm.push.service.EventService;
import org.obm.push.utils.SerializableInputStream;
import org.obm.sync.calendar.EventExtId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MailViewToMSEmailConverterImpl implements MailViewToMSEmailConverter {
	
	private static final Logger logger = LoggerFactory.getLogger(MailViewToMSEmailConverterImpl.class);
	private static final Charset DEFAULT_CHARSET = Charsets.ISO_8859_1;

	private final MSEmailHeaderConverter emailHeaderConverter;
	private final EventService eventService;

	@Inject
	@VisibleForTesting MailViewToMSEmailConverterImpl(MSEmailHeaderConverter emailHeaderConverter,
			EventService eventService) {
		
		this.emailHeaderConverter = emailHeaderConverter;
		this.eventService = eventService;
	}
	
	@Override
	public MSEmail convert(EmailView emailView, UserDataRequest userDataRequest) throws DaoException {
		MSEmailBuilder msEmailBuilder = new MSEmail.MSEmailBuilder();
		msEmailBuilder.uid(emailView.getUid());
		
		fillFlags(msEmailBuilder, emailView);
		msEmailBuilder.header(convertHeader(emailView));
		msEmailBuilder.body(convertBody(emailView));
		msEmailBuilder.attachements(convertAttachment(emailView));
		
		MSMeetingRequest msMeetingRequest = convertICalendar(emailView);
		msEmailBuilder.meetingRequest(fillMSEventUid(msMeetingRequest, userDataRequest));
		msEmailBuilder.messageClass(convertInvitationType(emailView));
		
		return msEmailBuilder.build();
	}

	private MSMeetingRequest fillMSEventUid(MSMeetingRequest msMeetingRequest, UserDataRequest userDataRequest) throws DaoException {
		if (msMeetingRequest != null) {
			EventExtId eventExtId = new EventExtId(msMeetingRequest.getMSEventExtId().serializeToString());
			MSEventUid msEventUid = eventService.getMSEventUidFor(eventExtId, userDataRequest.getDevice());
			return new MSMeetingRequest.MsMeetingRequestBuilder()
				.copy(msMeetingRequest)
				.msEventUid(msEventUid).build();
		} else {
			return null;
		}
	}

	private void fillFlags(MSEmailBuilder msEmailBuilder, EmailView emailView) {
		msEmailBuilder.answered(emailView.hasFlag(Flag.ANSWERED));
		msEmailBuilder.read(emailView.hasFlag(Flag.SEEN));
		msEmailBuilder.starred(emailView.hasFlag(Flag.FLAGGED));
	}
	
	private MSEmailHeader convertHeader(EmailView emailView) {
		return emailHeaderConverter.convertToMSEmailHeader(emailView.getEnvelope());
	}

	private MSEmailBody convertBody(EmailView emailView) {
		SerializableInputStream mimeData = new SerializableInputStream(emailView.getBodyMimePartData());
		Integer bodyTruncation = emailView.getBodyTruncation();
		
		return new MSEmailBody(mimeData, convertContentType(emailView), 
				bodyTruncation, chooseSupportedCharset(emailView.getCharset()));
	}

	private Charset chooseSupportedCharset(String charset) {
		if (Strings.isNullOrEmpty(charset)) {
			return DEFAULT_CHARSET;
		}
		
		try {
			if (Charset.isSupported(charset)) {
				return Charset.forName(charset);
			}
		} catch (IllegalCharsetNameException e) {
			logger.warn(e.getMessage());
		} catch (UnsupportedCharsetException e) {
			logger.warn(e.getMessage());
		}
		return DEFAULT_CHARSET;
	}
	
	private MSEmailBodyType convertContentType(EmailView emailView) {
		MSEmailBodyType msEmailBodyType = MSEmailBodyType.fromMimeType(emailView.getContentType().getFullMimeType());
		if (msEmailBodyType == null) {
			msEmailBodyType = MSEmailBodyType.MIME;
		}
		return msEmailBodyType;
	}

	private Set<MSAttachement> convertAttachment(EmailView emailView) {
		Set<MSAttachement> msAttachments = Sets.newHashSet();
		if (emailView.getAttachments() != null) {
			for (EmailViewAttachment attachment : emailView.getAttachments()) {
				MSAttachement msAttachment = new MSAttachement();
				msAttachment.setDisplayName(attachment.getDisplayName());
				msAttachment.setEstimatedDataSize(attachment.getSize());
				msAttachment.setFileReference(attachment.getFileReference());
				msAttachment.setMethod(MethodAttachment.NormalAttachment);
				msAttachments.add(msAttachment);
			}
		}
		return msAttachments;
	}
	
	private boolean isSupportedICalendar(EmailView emailView) {
		ICalendar iCalendar = emailView.getICalendar();
		if (iCalendar != null) {
			ICalendarEvent iCalendarEvent = iCalendar.getICalendarEvent();
			if (iCalendarEvent != null && iCalendarEvent.isVEvent()) {
				return iCalendarEvent.reccurenceId() == null;
			}
		}
		return false;
	}
	
	private MSMeetingRequest convertICalendar(EmailView emailView) {
		if (isSupportedICalendar(emailView)) {
			return new ICalendarConverter().convertToMSMeetingRequest(emailView.getICalendar());
		}
		return null;
	}

	private MSMessageClass convertInvitationType(EmailView emailView) {
		if (isSupportedICalendar(emailView)) {
			if (emailView.getInvitationType() == EmailViewInvitationType.REQUEST) {
				return MSMessageClass.SCHEDULE_MEETING_REQUEST;
			}
			if (emailView.getInvitationType() == EmailViewInvitationType.CANCELED) {
				return MSMessageClass.SCHEDULE_MEETING_CANCELED;
			}
		}
		return null;
	}
}
