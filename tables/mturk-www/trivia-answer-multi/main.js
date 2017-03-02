$(function() {
  
  // Get URL Parameters (GUP)
  function gup (name) {
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)");
    var results = regex.exec(window.location.href);
    return (results === null) ? "" : results[1];
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

  function clean(text, cleanNewlines) {
    var answer = [];
    text.split(/\n/).forEach(function (x) {
      x = x.replace(/\s+/g, ' ').replace(/^ | $/g, '');
      if (x.length) answer.push(x);
    });
    return answer.join(cleanNewlines ? " " : "\n");
  }

  $('form').submit(function() {
    if (!iWillSubmit) return false;
    // Check if all text fields are filled.
    var ok = true;
    $('#questionWrapper textarea').each(function (i, elt) {
      var text = clean($(elt).val());
      $(elt).val(text);
      if (!text.length && i < questionDivs.length - 1) ok = false;
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
  // Navigation

  var questionDivs = [], webpageDivs = [], currentQuestion = 0;

  function goToQuestion(questionNum) {
    $('.question, .webpage').hide();
    questionDivs[questionNum].show();
    if (questionNum < webpageDivs.length) {
      webpageDivs[questionNum].show();
      scrollTo(webpageDivs[questionNum].find('.highlightedTable'));
    }
    $('#qNum').text('' + (questionNum + 1) + ' / ' + questionDivs.length);
    $('#prevButton').prop('disabled', questionNum === 0);
    $('#nextButton').toggle(questionNum !== questionDivs.length - 1);
    $('#submitButton').toggle(questionNum === questionDivs.length - 1);
    currentQuestion = questionNum;
    $('#qNum').trigger('questionChanged');
  }

  $('#prevButton').click(function () {
    goToQuestion(currentQuestion - 1);
  });
  
  $('#nextButton').click(function () {
    goToQuestion(currentQuestion + 1);
  });

  ////////////////////////////////////////////////////////////////
  // Load data

  $.fn.findAll = function(selector) {
    return this.filter(selector).add(this.find(selector));
  };
  
  function scrollTo(elt) {
    $('html, body').scrollTop($(elt).offset().top - $(window).height() * 0.25);
  }

  var remainingPages;

  function populateData(data, i) {
    // Display questions
    var answerBox;
    questionDivs.push(
      $('<div class=question>').hide()
        .append($('<input type=hidden>').attr('name', 'm' + i + 'title').val(data.title))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'url').val(data.url))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'hash').val(data.hashcode))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'pageid').val(data.id))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'tableid').val(data.tableIndex || 0))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'srcBatchIndex').val(data.srcBatchIndex))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'srcDataIndex').val(data.srcDataIndex))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'oldAnsBatchIndex').val(data.oldAnsBatchIndex))
        .append($('<input type=hidden>').attr('name', 'm' + i + 'oldAnsDataIndex').val(data.oldAnsDataIndex))
        .append($('<h2>Question</h2>'))
        .append($('<div class=questionText>').text(data.question))
        .append(answerBox = $('<textarea class=answerBox disabled rows=5>').attr('name', 'a' + i).attr('id', 'a' + i))
        .append($('<input type=hidden>').attr('name', 'q' + i).attr('id', 'q' + i).val(data.question))
        .append($('<input type=hidden>').attr('name', 'c' + i).attr('id', 'c' + i).val(data.prompt))
        .append($('<p class=squeeze>').text('Please read the instructions for these special buttons:'))
        .append($('<button type=button>').text('not answerable by the highlighted table').click(function () {answerBox.val('[[n/a]]');}))
        .append($('<button type=button>').text('requires long calculation').click(function () {answerBox.val('[[long-calculation]]');}))
        .append($('<button type=button>').text('multiple ways to answer').click(function () {answerBox.val('[[multiple-ways]]');}))
        .appendTo('#questionWrapper'));
    // Load web pages
    var pageURL = "data/" + data.srcBatchIndex + "-page/" + data.srcDataIndex + ".html";
    $.get(pageURL, function (content) {
      var page = $(content), pageDiv;
      page.findAll('input, button, a').attr('tabindex', -1);
      page.findAll('a').attr('href', null);
      page.findAll('form').submit(function() {return false;});
      webpageDivs[i] = (pageDiv = $('<div class=webpage>')
          .append($('<h1 class="webpage-title">').text(data.title))
          .append(page).hide().appendTo('#webpage'));
      var theTable = $(pageDiv.find('table.wikitable')[data.tableIndex]);
      theTable.addClass('highlightedTable');
      theTable.on("click", "th, td", function (event) {
        if (event.ctrlKey) {
          var answerBox = questionDivs[currentQuestion].find('.answerBox');
          var originalAnswer = answerBox.val();
          if (originalAnswer.length && originalAnswer[originalAnswer.length - 1] !== '\n')
            originalAnswer += '\n';
          answerBox.val(originalAnswer + clean($(this).text(), true) + '\n');
        }
      });
      remainingPages -= 1;
      if (!remainingPages) {
        goToQuestion(0);
        // Show / Hide instructions
        $("#hideInstructionButton").text("Hide").prop('disabled', false);
        toggleInstructions(noAssignmentId);
        if (!noAssignmentId) {
          $('.question textarea, .question input, #submitButton').prop('disabled', false);
          $('#a1').focus();
        }
      }
    }, 'html');
  }

  $.getJSON("data/" + batchIndex + "-json/" + dataIndex + '.json', function (allData) {
    remainingPages = allData.length;
    allData.forEach(populateData); 
    questionDivs.push(
      $('<div class=question>').hide()
        .append($('<h2>Comments / Suggestions (Optional)</h2>'))
        .append($('<textarea disabled>').attr('name', 'comment').attr('id', 'comment'))
        .appendTo('#questionWrapper'));
  });

});
