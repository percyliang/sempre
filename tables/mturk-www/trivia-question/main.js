$(function() {
  
  // Get URL Parameters (GUP)
  function gup (name) {
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)");
    var results = regex.exec(window.location.href);
    return (results === null) ? "" : results[1];
  }

  // Decode the query parameters that were URL-encoded
  function decodeParam (strToDecode) {
    return unescape(strToDecode.replace(/\+/g, " "));
  }

  // Get the assignment ID
  var assignmentId = gup("assignmentId");
  var noAssignmentId = (!assignmentId ||
                        assignmentId === "ASSIGNMENT_ID_NOT_AVAILABLE");
  var batchIndex = gup("batchId");
  var dataIndex = gup("dataId");
  if (!(/^[0-9]+$/.test(batchIndex) && (/^[0-9]+$/.test(dataIndex)))) return;
  batchIndex = +batchIndex;
  dataIndex = +dataIndex;
  $("#assignmentId").val(assignmentId);
  $("#batchIndex").val(batchIndex);
  $("#dataIndex").val(dataIndex);
  $("#email").attr("href", $("#email").attr("href") + "%20#" + batchIndex + "-" + dataIndex);
  if (noAssignmentId) {
    // We are previewing the HIT. Display helpful message
    $("#warning").show();
  }
  if (gup("turkSubmitTo").indexOf("workersandbox") !== -1) {
    // Sandbox mode
    $("#answerForm")
      .attr("action", "https://workersandbox.mturk.com/mturk/externalSubmit");
  } else if (gup("debug") === "true") {
    // Debug mode
    $("#answerForm")
      .attr("action", "javascript:alert('debug!')");
  } else {
    // Real mode
    $("#answerForm")
      .attr("action", "https://www.mturk.com/mturk/externalSubmit");
  }

  ////////////////////////////////////////////////////////////////
  // Form submission

  var iWillSubmit = false;

  $('#submitButton').click(function () {
    iWillSubmit = true;
    $('#answerForm').submit();
  });
  
  $('#submitButton').keyup(function(e) {
    var keyCode = e.keyCode || e.which;
    if (keyCode === 13 || keyCode === 32) {
      iWillSubmit = true;
      $('#answerForm').submit();
    }
  });

  function cleanText(x) {
    return x.replace(/\s+/g, ' ').replace(/^ | $/g, '');
  }

  $('form').submit(function() {
    if (!iWillSubmit) return false;
    // Check if all text fields are filled.
    var ok = true;
    $('#questionWrapper textarea').each(function (i, elt) {
      var text = cleanText($(elt).val());
      $(elt).val(text);
      if (!text.length) ok = false;
    });
    if (ok) {
      $('#incompleteWarning').hide();
      $('#submitButton').prop('disabled', true);
      return true;
    } else {
      $('#incompleteWarning').show();
      return false;
    }
  });

  ////////////////////////////////////////////////////////////////
  // Instructions

  function toggleInstructions(state) {
    $("#showingInstruction").toggle(!!state);
    $("#hidingInstruction").toggle(!state);
  }

  $("#showInstructionButton").click(function () {
    toggleInstructions(true);
  });

  $("#hideInstructionButton").click(function () {
    toggleInstructions(false);
  });

  ////////////////////////////////////////////////////////////////
  // Load data

  var i1 = -1, i2 = -1, i3 = -1, i4 = -1;

  function randomizeTemplate(k) {
    var n = TEMPLATES.length, i;
    do {
      i = Math.floor(Math.random() * (n-1));
    } while (i == i1 || i == i2 || i == i3 || i == i4);
    $('#c' + k).val(TEMPLATES[i]); $('#c' + k + 'text').text(TEMPLATES[i]);
    return i;
  }

  $('#s1').click(function () {i1 = randomizeTemplate(1); $('#shuf1').val(+$('#shuf1').val() + 1);});
  $('#s2').click(function () {i2 = randomizeTemplate(2); $('#shuf2').val(+$('#shuf2').val() + 1);});
  $('#s3').click(function () {i3 = randomizeTemplate(3); $('#shuf3').val(+$('#shuf3').val() + 1);});
  $('#s4').click(function () {i4 = randomizeTemplate(4); $('#shuf4').val(+$('#shuf4').val() + 1);});

  function randomizeTemplates() {
    i1 = randomizeTemplate(1);
    i2 = randomizeTemplate(2);
    i3 = randomizeTemplate(3);
    i4 = randomizeTemplate(4);
  }

  $.fn.findAll = function(selector) {
    return this.filter(selector).add(this.find(selector));
  };
  
  function scrollTo(elt) {
    $('html, body').animate({'scrollTop': $(elt).offset().top - $(window).height() * .25});
  }

  function populateData(data) {
    $('input[name=title]').val(data.title);
    $('input[name=url]').val(data.url);
    $('input[name=hash]').val(data.hashcode);
    $('input[name=pageid]').val(data.id);
    $('input[name=tableid]').val(data.tableIndex || 0);
    randomizeTemplates();
    // Load web pages
    $.get("data/" + batchIndex + "-page/" + dataIndex + '.html', function (content) {
      var page = $(content);
      page.findAll('input, button, a').attr('tabindex', -1);
      page.findAll('a').attr('href', null);
      page.findAll('form').submit(function() {return false;});
      $('#webpage').empty().append($('<h1>').text(data.title)).append(page);
      // Find the first wikitable
      var theTable = $($('#webpage').find('table.wikitable')[data.tableIndex || 0]);
      theTable.addClass('highlightedTable');
      $('#lastRow').val(cleanText(theTable.find('tr:last').text()));
      $('html, body').scrollTop(0);
      setTimeout(function () {
        scrollTo(theTable);
      }, 700);
      // Show / Hide instructions
      $("#hideInstructionButton").text("Hide").prop('disabled', false);
      toggleInstructions(noAssignmentId);
      if (!noAssignmentId) {
        $('.question input, #submitButton').prop('disabled', false);
        $('#a1').focus();
      }
    }, 'html');
  }

  $.getJSON("data/" + batchIndex + "-json/" + dataIndex + '.json', populateData);

});
