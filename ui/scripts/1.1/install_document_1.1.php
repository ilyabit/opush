<script language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - File : install_document.php                                         //
//     - Desc : OBM install : create default Document repository             //
// 2005-08-14 Pierre Baudracco                                               //
///////////////////////////////////////////////////////////////////////////////
// $Id$
///////////////////////////////////////////////////////////////////////////////

$obminclude = getenv("OBM_INCLUDE_VAR");
if ($obminclude == "") $obminclude = "../../obminclude";
include("$obminclude/global.inc");
include("../../php/admin_data/admin_data_query.inc");

// Check Document path is set
if (! isset($cdocument_root)) {
  echo "The document repository root is not set ! (\$cdocument_root)\n";
  exit(1);
}

// If document root does not exist, try to create it
if (! is_dir($cdocument_root)) {
  echo "The document repository root does not exist. Trying to create it\n";
  if (! mkdir($cdocument_root)) {
    echo "The document repository root can not be created. Check the path\n";
    exit(1);
  }
}

// Check that repository root is writable
$files = get_file_list($cdocument_root);
$nb = count($files);
if (! is_writable($cdocument_root)) {
  echo "Document repository root, is not writable. Check user and access rights !\n";
  exit(1);
}


// Check that repository is empty, else alert and exit
$files = get_file_list($cdocument_root);
$nb = count($files);
if ($nb > 0) {
  echo "Document repository is not empty ! Install stopped\n";
  exit(1);
}


// Populate the repository root with storage dirs
for ($dir_num = 0; $dir_num < 10; $dir_num++) {
  $sdir = "$cdocument_root/$dir_num";
  if (! mkdir($sdir)) {
    echo "Error creating the storage directory : $sdir !\n";
    exit(1);
  } else {
    chmod($sdir, 0777);
  }
}

echo "Success : Document repository created and initialized\n";
exit(0);
</script>
