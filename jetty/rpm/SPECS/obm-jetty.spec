Name: obm-jetty
Version: %{obm_version}
Release: %{obm_release}%{?dist}
Summary: configuration for Jetty for Open Business Management
Vendor: obm.org
URL: http://www.obm.org
Group: Development/Tools
License: GPLv2+
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)
Source0: jetty.xml.sample
Source1: jetty-logging.xml.sample

BuildArch: noarch
Requires(post): jetty6


%description
It allows Jetty Server to start after its install and changes the port.

OBM is a global groupware, messaging and CRM application. It is intended to
be an Exchange Or Notes/Domino Mail replacement, but can also be used as a
simple contact database. OBM also features integration with PDAs, smartphones,
Mozilla Thunderbird/Lightning and Microsoft Outlook via specific connectors.

%install
mkdir -p $RPM_BUILD_ROOT%{_docdir}/obm-jetty
install -p -m 755 %{SOURCE0} $RPM_BUILD_ROOT%{_docdir}/obm-jetty/jetty.xml.sample
install -p -m 755 %{SOURCE1} $RPM_BUILD_ROOT%{_docdir}/obm-jetty/jetty-logging.xml.sample

%files
%defattr(-,root,root,-)
%{_docdir}/obm-jetty/jetty.xml.sample
%{_docdir}/obm-jetty/jetty-logging.xml.sample

%post
service jetty6 stop > /dev/null 2>&1 || :
if [ -e %{_sysconfdir}/jetty6/jetty.xml ] && [ `diff %{_docdir}/obm-jetty/jetty.xml.sample %{_sysconfdir}/jetty6/jetty.xml` -ne 0 ]; then
	cp %{_sysconfdir}/jetty6/jetty.xml %{_sysconfdir}/jetty6/jetty.xml.orig
fi
cp %{_docdir}/obm-jetty/jetty.xml.sample %{_sysconfdir}/jetty6/jetty.xml

if [ -e %{_sysconfdir}/jetty6/jetty-logging.xml ] && [ `diff %{_docdir}/obm-jetty/jetty-logging.xml.sample %{_sysconfdir}/jetty6/jetty-logging.xml` ]; then
	cp %{_sysconfdir}/jetty6/jetty-logging.xml %{_sysconfdir}/jetty6/jetty-logging.xml.orig
fi
cp %{_docdir}/obm-jetty/jetty-logging.xml.sample %{_sysconfdir}/jetty6/jetty-logging.xml
if [ `grep -F %{_sysconfdir}/jetty6/jetty-logging.xml %{_sysconfdir}/jetty6/jetty-logging.xml` -ne 0 ]; then
    echo %{_sysconfdir}/jetty6/jetty-logging.xml >> %{_sysconfdir}/jetty6/jetty-logging.xml
fi
service jetty6 start > /dev/null 2>&1 || :
