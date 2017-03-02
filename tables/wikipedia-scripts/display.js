$(function() {
  
  // Get URL Parameters (GUP)
  function gup (name) {
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)");
    var results = regex.exec(window.location.href);
    return (results === null) ? "" : results[1];
  }

  function displayError(message) {
    $('#webpage').empty().append(
      $('<div>').addClass('cannot-load').html('<strong>Error:</strong> ' + message));
  }

  function go(id) {
    if (!id) {
      displayError('No page id specified.');
    } else {
      $.getJSON("wikidump.cache/output/" + id + ".json", function (meta) {
        $('#title').text(meta.title);
        $('#subtitle').empty()
          .append('id: ' + meta.id)
          .append(' | revision: ' + meta.revision)
          .append(' | hashcode: ' + meta.hashcode)
          .append(' | ').append($('<a target=_blank>').text('link').attr('href', meta.url));
        $.get("wikidump.cache/output/" + id + ".html", function (content) {
          var page = $(content);
          page.find('img').attr('alt', '(image)');
          page.find('form').submit(function() {return false;});
          $('#webpage').empty().append(page);
          $("html, body").scrollTop(0);
        }, 'html').fail(function() {
          displayError('Cannot load <strong>' + id + '.html</strong>');
        });
      }).fail(function() {
        displayError('Cannot load <strong>' + id + '.json</strong>');
      });
    }
  }

  go(gup('id'));

  $("#header input").click(function () {
    $(this).val("");
  });

  $("#header input").keypress(function (e) {
    var key = e.charCode ? e.charCode : e.keyCode ? e.keyCode : 0;
    if (key == 13) {
      e.preventDefault();
        go($("#header input").val());
    }
  });

  $("#header button").click(function () {
    go($("#header input").val());
  });

});
