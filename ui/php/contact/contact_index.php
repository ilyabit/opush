<SCRIPT language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - File : contact_index.php                                            //
//     - Desc : Contact Index File                                           //
// 1999-03-19 Pierre Baudracco                                               //
///////////////////////////////////////////////////////////////////////////////
// $Id$ //
///////////////////////////////////////////////////////////////////////////////
// Actions :
// - index (default) -- search fields  -- show the contact search form
// - search          -- search fields  -- show the result set of search
// - new             -- $param_company -- show the new contact form
// - detailconsult   -- $param_contact -- show the contact detail
// - detailupdate    -- $param_contact -- show the contact detail form
// - insert          -- form fields    -- insert the company
// - update          -- form fields    -- update the company
// - check_delete    -- $param_contact -- check links before delete
// - delete          -- $param_contact -- delete the company
// - admin           --                -- admin index (kind)
// - kind_insert     -- form fields    -- insert the kind
// - kind_update     -- form fields    -- update the kind
// - kind_checklink  --                -- check if kind is used
// - kind_delete     -- $sel_kind      -- delete the kind
// - display         --                -- display and set display parameters
// - dispref_display --                -- update one field display value
// - dispref_level   --                -- update one field display position 
// External API ---------------------------------------------------------------
// - ext_get_ids     --                -- select multiple contacts (return id) 
///////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// Session,Auth,Perms  Management                                            //
///////////////////////////////////////////////////////////////////////////////
$path = "..";
$section = "COM";
$menu = "CONTACT";
$obminclude = getenv("OBM_INCLUDE_VAR");
if ($obminclude == "") $obminclude = "obminclude";
require("$obminclude/phplib/obmlib.inc");
include("$obminclude/global.inc");
page_open(array("sess" => "OBM_Session", "auth" => "OBM_Challenge_Auth", "perm" => "OBM_Perm"));
include("$obminclude/global_pref.inc");
require("contact_display.inc");
require("contact_query.inc");


$uid = $auth->auth["uid"];

// updating the contact bookmark : 
if ( ($param_contact == $last_contact) && (strcmp($action,"delete")==0) ) {
  $last_contact = $last_contact_default;
} else if ( ($param_contact > 0) && ($last_contact != $param_contact) ) {
  $last_contact = $param_contact;
  run_query_set_user_pref($auth->auth["uid"],"last_contact",$param_contact);
  $last_contact_name = run_query_global_contact_name($last_contact);
  //$sess->register("last_contact");  
}

page_close();

if($action == "") $action = "index";
$contact = get_param_contact();
get_contact_action();
$perm->check();


if (! $contact["popup"]) {
  $display["header"] = generate_menu($menu,$section);
}

///////////////////////////////////////////////////////////////////////////////
// External calls (main menu not displayed)                                  //
///////////////////////////////////////////////////////////////////////////////
if ($action == "ext_get_ids") {
  $display["search"] = html_contact_search_form($contact);
  if ($set_display == "yes") {
    $display["result"] = dis_contact_search_list($contact);
  } else {
    $display["msg"] .= display_ok_msg($l_no_display);
  }
}

///////////////////////////////////////////////////////////////////////////////
// Main Program                                                              //
///////////////////////////////////////////////////////////////////////////////

if ($action == "index" || $action == "") {
///////////////////////////////////////////////////////////////////////////////
  $display["search"] = html_contact_search_form($contact);
  if ($set_display == "yes") {
    $display["result"] = dis_contact_search_list($contact);
  } else {
    $display["msg"] .= display_info_msg($l_no_display);
  }
  
} elseif ($action == "search")  {
///////////////////////////////////////////////////////////////////////////////
  $display["search"] = html_contact_search_form($contact);
  $display["result"] = dis_contact_search_list($contact);
  
} elseif ($action == "new")  {
///////////////////////////////////////////////////////////////////////////////
  if (isset($param_company)) {
    $comp_q = run_query_contact_company($param_company);
  }
  $kind_q = run_query_kind();
  require("contact_js.inc");
  $display["detail"] = html_contact_form($action, $comp_q, $kind_q, $contact);

} elseif ($action == "detailconsult")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_contact > 0) {
    $con_q = run_query_detail($param_contact);
    if ($con_q->num_rows() != 1) {
      $display["msg"] .= display_err_msg($l_query_error . " - " . $con_q->query . " !");
    }
    if ( ($con_q->f("contact_visibility")==0) || ($con_q->f("contact_usercreate") == $uid) ) {
      $display["detailInfo"] = display_record_info($con_q->f("contact_usercreate"),$con_q->f("contact_userupdate"),$con_q->f("timecreate"),$con_q->f("timeupdate")); 	    
      $display["detail"] = html_contact_consult($con_q);
    } else {
      // this contact's page has "private" access
      $display["msg"] .= display_err_msg($l_error_visibility);
    }      
  }
  
} elseif ($action == "detailupdate")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_contact > 0) {
    $con_q = run_query_detail($param_contact);
    if ($con_q->num_rows() == 1) {
      $kind_q = run_query_kind();
    } else {
      $display["msg"] .= display_err_msg($l_query_error . " - " . $con_q->query . " !");
    }
    require("contact_js.inc");
    $display["detailInfo"] = display_record_info($con_q->f("contact_usercreate"),$con_q->f("contact_userupdate"),$con_q->f("timecreate"),$con_q->f("timeupdate")); 
    $display["detail"] = html_contact_form($action, $con_q, $kind_q, $contact);
  }

} elseif ($action == "insert")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form("", $contact)) {

    // If the context (same contacts) was confirmed ok, we proceed
    if ($hd_confirm == $c_yes) {
      $retour = run_query_insert($contact);
      if ($retour) {
        $dislay["msg"] .= display_ok_msg($l_insert_ok);
      } else {
        $dislay["msg"] .= display_err_msg($l_insert_error);
      }
      $dislay["search"] = html_contact_search_form($contact);

    // If it is the first try, we warn the user if some contacts seem similar
    } else {
      $obm_q = check_contact_context("", $contact);
      if ($obm_q->num_rows() > 0) {
        dis_contact_warn_insert("", $obm_q, $contact);
      } else {
        $retour = run_query_insert($contact);
        if ($retour) {
          $dislay["msg"] .= display_ok_msg($l_insert_ok);
        } else {
          $dislay["msg"] .= display_err_msg($l_insert_error);
        }
        html_contact_search_form($contact);
      }
    }

  // Form data are not valid
  } else {
    $dislay["msg"] .= display_warn_msg($l_invalid_data . " : " . $err_msg);
    $kind_q = run_query_kind();
    html_contact_form($action, "", $kind_q, $contact);
  }
  
} elseif ($action == "update")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form("", $contact)) {
    $retour = run_query_update($contact);
    if ($retour) {
      $dislay["msg"] .= display_ok_msg($l_update_ok);
    } else {
      $dislay["msg"] .= display_err_msg($l_update_error);
    }
    $con_q = run_query_detail($param_contact);
    display_record_info($con_q->f("contact_usercreate"),$con_q->f("contact_userupdate"),$con_q->f("timecreate"),$con_q->f("timeupdate")); 	    
    html_contact_consult($con_q);
  } else {
    $dislay["msg"] .= display_err_msg($l_invalid_data . " : " . $err_msg);
    $kind_q = run_query_kind();
    html_contact_form($action, "", $kind_q, $contact);
  }
  
} elseif ($action == "check_delete")  {
//////////////////////////////////////////////////////////////////////////////
  require("contact_js.inc");
  dis_check_links($param_contact);
  
} elseif ($action == "delete")  {
//////////////////////////////////////////////////////////////////////////////
  $retour = run_query_delete($param_contact);
  if ($retour) {
    $dislay["msg"] .= display_ok_msg($l_delete_ok);
  } else {
    $dislay["msg"] .= display_err_msg($l_delete_error);
  }
  html_contact_search_form($contact);
    
} elseif ($action == "admin")  {
///////////////////////////////////////////////////////////////////////////////
   $dislay["msg"] .= display_err_msg("Nothing admin here for now.");
   
}  elseif ($action == "display") {
///////////////////////////////////////////////////////////////////////////////
  $pref_q = run_query_display_pref($uid, "contact", 1);
  dis_contact_display_pref($pref_q); 
  
} else if($action == "dispref_display") {
///////////////////////////////////////////////////////////////////////////////
  run_query_display_pref_update($entity, $fieldname, $disstatus);
  $pref_q = run_query_display_pref($uid, "contact", 1);
  dis_contact_display_pref($pref_q);
  
} else if($action == "dispref_level") {
///////////////////////////////////////////////////////////////////////////////
  run_query_display_pref_level_update($entity, $new_level, $fieldorder);
  $pref_q = run_query_display_pref($uid, "contact", 1);
  dis_contact_display_pref($pref_q);
}


///////////////////////////////////////////////////////////////////////////////
// Display
///////////////////////////////////////////////////////////////////////////////
$display["head"] = display_head($l_contact);
$display["end"] = display_end();

display_page($display);


///////////////////////////////////////////////////////////////////////////////
// Stores Contact parameters transmited in $contact hash
// returns : $contact hash with parameters set
///////////////////////////////////////////////////////////////////////////////
function get_param_contact() {
  global $action;
  global $sel_kind, $tf_lname, $tf_fname, $tf_company, $tf_ad1, $tf_ad2;
  global $tf_zip, $tf_town, $tf_cdx, $tf_ctry, $tf_func, $tf_phone, $tf_hphone;
  global $tf_mphone, $tf_fax, $tf_email, $cb_mailok, $ta_com, $cb_vis, $cb_archive;
  global $param_company, $param_contact, $hd_usercreate, $cdg_param;
  global $company_name, $company_new_name, $company_new_id;
  global $popup, $ext_action, $ext_url, $ext_id, $ext_target;

  if (isset ($param_contact)) $contact["id"] = $param_contact;
  if (isset ($hd_usercreate)) $contact["usercreate"] = $hd_usercreate;
  if (isset ($sel_kind)) $contact["kind"] = $sel_kind;
  if (isset ($tf_lname)) $contact["lname"] = trim($tf_lname);
  if (isset ($tf_fname)) $contact["fname"] = trim($tf_fname);
  if (isset ($param_company)) $contact["company_id"] = $param_company;
  if (isset ($tf_company)) $contact["company"] = $tf_company;
  if (isset ($company_name)) $contact["company_name"] = $company_name;
  if (isset ($company_new_name)) $contact["comp_new_name"] = $company_new_name;
  if (isset ($company_new_id)) $contact["comp_new_id"] = $company_new_id;
  if (isset ($tf_ad1)) $contact["ad1"] = $tf_ad1;
  if (isset ($tf_ad2)) $contact["ad2"] = $tf_ad2;
  if (isset ($tf_zip)) $contact["zip"] = $tf_zip;
  if (isset ($tf_town)) $contact["town"] = $tf_town;
  if (isset ($tf_cdx)) $contact["cdx"] = $tf_cdx;
  if (isset ($tf_ctry)) $contact["ctry"] = $tf_ctry;
  if (isset ($tf_func)) $contact["func"] = $tf_func;
  if (isset ($tf_phone)) $contact["phone"] = trim($tf_phone);
  if (isset ($tf_hphone)) $contact["hphone"] = trim($tf_hphone);
  if (isset ($tf_mphone)) $contact["mphone"] = trim($tf_mphone);
  if (isset ($tf_fax)) $contact["fax"] = trim($tf_fax);
  if (isset ($tf_email)) $contact["email"] = trim($tf_email);
  if (isset ($cb_archive)) $contact["archive"] = ($cb_archive == 1 ? 1 : 0);
  if (isset ($cb_vis)) $contact["vis"] = ($cb_vis == 1 ? 1 : 0);
  if (isset ($cb_mailok)) $contact["mailok"] = ($cb_mailok == 1 ? 1 : 0);
  if (isset ($ta_com)) $contact["com"] = $ta_com;

  // External param
  if (isset ($popup)) $contact["popup"] = $popup;
  if (isset ($ext_action)) $contact["ext_action"] = $ext_action;
  if (isset ($ext_url)) $contact["ext_url"] = $ext_url;
  if (isset ($ext_id)) $contact["ext_id"] = $ext_id;
  if (isset ($ext_target)) $contact["ext_target"] = $ext_target;

  if (debug_level_isset($cdg_param)) {
    echo "<br />action=$action";
    if ( $contact ) {
      while ( list( $key, $val ) = each( $contact ) ) {
        echo "<br />contact[$key]=$val";
      }
    }
  }

  return $contact;
}


///////////////////////////////////////////////////////////////////////////////
//  Contact Action 
///////////////////////////////////////////////////////////////////////////////
function get_contact_action() {
  global $contact, $actions, $path;
  global $l_header_find,$l_header_new,$l_header_update,$l_header_delete;
  global $l_header_consult, $l_header_display, $l_header_admin;
  global $contact_read, $contact_write, $contact_admin_read, $contact_admin_write;

// Index
  $actions["CONTACT"]["index"] = array (
    'Name'     => $l_header_find,
    'Url'      => "$path/contact/contact_index.php?action=index",
    'Right'    => $contact_read,
    'Condition'=> array ('all') 
                                        );

// Search
  $actions["CONTACT"]["search"] = array (
    'Url'      => "$path/contact/contact_index.php?action=search",
    'Right'    => $contact_read,
    'Condition'=> array ('None') 
                                    	);

// New
  $actions["CONTACT"]["new"] = array (
    'Name'     => $l_header_new,
    'Url'      => "$path/contact/contact_index.php?action=new",
    'Right'    => $contact_write,
    'Condition'=> array ('','index','search','new','detailconsult','update','admin','display') 
                                     );

// Detail Consult
 $actions["CONTACT"]["detailconsult"]   = array (
    'Name'     => $l_header_consult,
    'Url'      => "$path/contact/contact_index.php?action=detailconsult&amp;param_contact=".$contact["id"]."",
    'Right'    => $contact_read,
    'Condition'=> array ('detailupdate') 
                                    		 );

// Detail Update
  $actions["CONTACT"]["detailupdate"] = array (
    'Name'     => $l_header_update,
    'Url'      => "$path/contact/contact_index.php?action=detailupdate&amp;param_contact=".$contact["id"]."",
    'Right'    => $contact_write,
    'Condition'=> array ('detailconsult', 'update') 
                                     		 );

// Insert
  $actions["CONTACT"]["insert"] = array (
    'Url'      => "$path/contact/contact_index.php?action=insert",
    'Right'    => $contact_write,
    'Condition'=> array ('None') 
                                     	);

// Update
  $actions["CONTACT"]["update"] = array (
    'Url'      => "$path/contact/contact_index.php?action=update",
    'Right'    => $contact_write,
    'Condition'=> array ('None') 
                                     	);

// Check Delete
  $actions["CONTACT"]["check_delete"] = array (
    'Name'     => $l_header_delete,
    'Url'      => "$path/contact/contact_index.php?action=check_delete&amp;param_contact=".$contact["id"]."",
    'Right'    => $contact_write,
    'Condition'=> array ('detailconsult', 'detailupdate', 'update') 
                                     	      );

// Delete
  $actions["CONTACT"]["delete"] = array (
    'Url'      => "$path/contact/contact_index.php?action=delete",
    'Right'    => $contact_write,
    'Condition'=> array ('None') 
                                     	);

// Admin
  $actions["CONTACT"]["admin"] = array (
    'Name'     => $l_header_admin,
    'Url'      => "$path/contact/contact_index.php?action=admin",
    'Right'    => $contact_admin_read,
    'Condition'=> array ('all') 
                                      		 );

// Dispay
  $actions["CONTACT"]["display"] = array (
    'Name'     => $l_header_display,
    'Url'      => "$path/contact/contact_index.php?action=display",
    'Right'    => $contact_read,
    'Condition'=> array ('all') 
                                      	 );

// Dispay Preferences
  $actions["CONTACT"]["displref_level"]	= array (
    'Url'      => "$path/contact/contact_index.php?action=dispref_display",
    'Right'    => $contact_read,
    'Condition'=> array ('None') 
                                      	        );

// Dispay Level
  $actions["CONTACT"]["displref_level"]= array (
    'Url'      => "$path/contact/contact_index.php?action=dispref_level",
    'Right'    => $contact_read,
    'Condition'=> array ('None') 
                                      		 );

}

</SCRIPT>
