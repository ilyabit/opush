package OBM::Entities::obmSystemUser;

$VERSION = "1.0";

$debug = 1;

use 5.006_001;
require Exporter;
use strict;

use OBM::Parameters::common;
use OBM::Parameters::ldapConf;
require OBM::Ldap::utils;
require OBM::passwd;
require OBM::toolBox;
require OBM::dbUtils;
use URI::Escape;
use Unicode::MapUTF8 qw(to_utf8 from_utf8 utf8_supported_charset);


sub new {
    my $self = shift;
    my( $incremental, $userId ) = @_;

    my %ldapEngineAttr = (
        type => undef,
        typeDesc => undef,
        incremental => undef,
        toDelete => undef,
        archive => undef,
        userId => undef,
        domainId => undef,
        userDesc => undef
    );


    if( !defined($userId) ) {
        croak( "Usage: PACKAGE->new(INCR, USERID)" );

    }elsif( $userId !~ /^\d+$/ ) {
        &OBM::toolBox::write_log( "obmSystemUser: identifiant d'utilisateur incorrect", "W" );
        return undef;

    }else {
        $ldapEngineAttr{"userId"} = $userId;
    }

    # Pas de mode incrémental pour ce type
    $ldapEngineAttr{"incremental"} = 0;

    $ldapEngineAttr{"type"} = $SYSTEMUSERS;
    $ldapEngineAttr{"typeDesc"} = $attributeDef->{$ldapEngineAttr{"type"}};
    $ldapEngineAttr{"toDelete"} = 0;

    bless( \%ldapEngineAttr, $self );
}


sub getEntity {
    my $self = shift;
    my( $dbHandler, $domainDesc ) = @_;
    my $userId = $self->{"userId"};


    if( !defined($dbHandler) ) {
        &OBM::toolBox::write_log( "obmSystemUser: connecteur a la base de donnee invalide", "W" );
        return 0;
    }

    if( !defined($domainDesc->{"domain_id"}) || ($domainDesc->{"domain_id"} !~ /^\d+$/) ) {
        &OBM::toolBox::write_log( "obmSystemUser: description de domaine OBM incorrecte", "W" );
        return 0;

    }else {
        # On positionne l'identifiant du domaine de l'entité
        $self->{"domainId"} = $domainDesc->{"domain_id"};
    }


    my $query = "SELECT COUNT(*) FROM UserSystem WHERE usersystem_id=".$userId;

    my $queryResult;
    if( !&OBM::dbUtils::execQuery( $query, $dbHandler, \$queryResult ) ) {
        &OBM::toolBox::write_log( "obmSystemUser: probleme lors de l'execution d'une requete SQL : ".$dbHandler->err, "W" );
        return undef;
    }

    my( $numRows ) = $queryResult->fetchrow_array();
    $queryResult->finish();

    if( $numRows == 0 ) {
        &OBM::toolBox::write_log( "obmSystemUser: pas d'utilisateur d'identifiant : ".$userId, "W" );
        return undef;
    }elsif( $numRows > 1 ) {
        &OBM::toolBox::write_log( "obmSystemUser: plusieurs utilisateurs d'identifiant : ".$userId." ???", "W" );
        return undef;
    }


    # La requete a executer - obtention des informations sur l'utilisateur
    $query = "SELECT usersystem_id, usersystem_login, usersystem_password, usersystem_uid, usersystem_gid, usersystem_homedir, usersystem_lastname, usersystem_firstname, usersystem_shell FROM UserSystem WHERE usersystem_id=".$userId;

    # On execute la requete
    if( !&OBM::dbUtils::execQuery( $query, $dbHandler, \$queryResult ) ) {
        &OBM::toolBox::write_log( "obmSystemUser: probleme lors de l'execution d'une requete SQL : ".$dbHandler->err, "W" );
        return undef;
    }

    # On range les resultats dans la structure de donnees des resultats
    my( $user_id, $user_login, $user_password, $user_uid, $user_gid, $user_homedir, $user_lastname, $user_firstname, $user_shell ) = $queryResult->fetchrow_array();
    $queryResult->finish();

    # Positionnement du flag archive
        &OBM::toolBox::write_log( "obmSystemUser: gestion de l'utilisateur '".$user_login."', domaine '".$domainDesc->{"domain_label"}."'", "W" );

        
    # On cree la structure correspondante a l'utilisateur
    # Cette structure est composee des valeurs recuperees dans la base
    $self->{"userDesc"} = {
        "user_id"=>$user_id,
        "user_login"=>$user_login,
        "user_uid"=>$user_uid,
        "user_gid"=>$user_gid,
        "user_lastname"=>$user_lastname,
        "user_firstname"=>$user_firstname,
        "user_homedir"=>$user_homedir,
        "user_passwd_type"=>"PLAIN",
        "user_passwd"=>$user_password,
        "user_shell"=>$user_shell,
        "user_domain" => $domainDesc->{"domain_label"}
    };


    return 1;
}


sub setDelete {
    my $self = shift;

    $self->{"toDelete"} = 1;

    return 1;
}


sub getDelete {
    my $self = shift;

    return $self->{"toDelete"};
}


sub getArchive {
    my $self = shift;

    return $self->{"archive"};
}


sub isIncremental {
    my $self = shift;

    return $self->{"incremental"};
}


sub getEntityLinks {
    my $self = shift;
    my( $dbHandler, $domainDesc ) = @_;

    return 1;
}


sub getLdapDnPrefix {
    my $self = shift;
    my $dnPrefix = undef;

    if( defined($self->{"typeDesc"}->{"dn_prefix"}) && defined($self->{"userDesc"}->{$self->{"typeDesc"}->{"dn_value"}}) ) {
        $dnPrefix = $self->{"typeDesc"}->{"dn_prefix"}."=".$self->{"userDesc"}->{$self->{"typeDesc"}->{"dn_value"}};
    }

    return $dnPrefix;
}


sub createLdapEntry {
    my $self = shift;
    my ( $ldapEntry ) = @_;
    my $entry = $self->{"userDesc"};

    # Gestion du mot de passe
    if( !defined( $entry->{"user_passwd_type"} ) || ($entry->{"user_passwd_type"} eq "") ) {
        return 0;
    }

    my $userPasswd = &OBM::passwd::convertPasswd( $entry->{"user_passwd_type"}, $entry->{"user_passwd"} );
    if( !defined( $userPasswd ) ) {
        return 0;
    }


    # On construit la nouvelle entree
    #
    # Les parametres nécessaires
    if( $entry->{"user_login"} && $entry->{"user_firstname"} && $entry->{"user_lastname"} && $entry->{"user_uid"} && defined($entry->{"user_gid"})  && $entry->{"user_homedir"} ) {

        my $longName;
        if( $entry->{"user_firstname"} ) {
            $longName = $entry->{"user_firstname"}." ".$entry->{"user_lastname"};
        }else {
            $longName = $entry->{"user_lastname"};
        }
                
        $ldapEntry->add(
            objectClass => $self->{"typeDesc"}->{"objectclass"},
            uid => to_utf8({ -string => $entry->{"user_login"}, -charset => $defaultCharSet }),
            cn => to_utf8({ -string => $longName, -charset => $defaultCharSet }),
            sn => to_utf8({ -string => $entry->{"user_lastname"}, -charset => $defaultCharSet }),
            uidNumber => $entry->{"user_uid"},
            gidNumber => $entry->{"user_gid"},
            homeDirectory => $entry->{"user_homedir"},
            loginShell => "/bin/bash",
            userpassword => $userPasswd,
            obmDomain => to_utf8({ -string => $entry->{"user_domain"}, -charset => $defaultCharSet })
        );

    }else {
        return 0;
    }

    return 1;
}


sub updateLdapEntry {
    my $self = shift;
    my( $ldapEntry ) = @_;
    my $entry = $self->{"userDesc"};
    my $update = 0;

    # Le champs nom, prenom de l'utilisateur
    my $longName = $entry->{"user_firstname"}." ".$entry->{"user_lastname"};
    if( &OBM::Ldap::utils::modifyAttr( $longName, $ldapEntry, "cn" ) ) {
        # On synchronise le surname
        &OBM::Ldap::utils::modifyAttr( $longName, $ldapEntry, "sn" );

        $update = 1;
    }

    # Le mot de passe
    if( defined( $entry->{"user_passwd_type"} ) && ($entry->{"user_passwd_type"} ne"") ) {
        my $userPasswd = &OBM::passwd::convertPasswd( $entry->{"user_passwd_type"}, $entry->{"user_passwd"} );
        if( defined( $userPasswd ) ) {
            if( &OBM::Ldap::utils::modifyAttr( $userPasswd, $ldapEntry, "userpassword" ) ) {
                $update = 1;
            }
        }
    }

    # Le domaine
    if( &OBM::Ldap::utils::modifyAttr( $entry->{"user_domain"}, $ldapEntry, "obmDomain") ) {
        $update = 1;
    }

    return $update;
}


sub getMailboxName {
    my $self = shift;

    return undef;
}


sub dump {
    my $self = shift;
    my @desc;

    push( @desc, $self );
    
    require Data::Dumper;
    print Data::Dumper->Dump( \@desc );

    return 1;
}
