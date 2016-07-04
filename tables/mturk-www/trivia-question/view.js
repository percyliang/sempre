$(function () {
  
  // Get URL Parameters (GUP)
  function gup (name) {
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)");
    var results = regex.exec(window.location.href);
    return (results === null) ? "" : results[1];
  }

  var batchIndex = +gup("batchId");
  var dataIndex = +gup("dataId");
  $.getJSON("data/" + batchIndex + "-results", readResults);

  function readResults(data) {
    var results = [];
    data.forEach(function (datum) {
      if (+datum.answers.batchIndex === batchIndex &&
          +datum.answers.dataIndex === dataIndex) {
        for (var key in datum.answers) {
          if (key[0] === 'a') {
            results.push(datum.answers[key]);
          }
        }
        results.push(null);
      }
    });
    $('#showAnswer').empty();
    results.forEach(function (x) {
      if (x === null) {
        $('<hr>').appendTo('#showAnswer');
      } else {
        $('<p>').text(x).appendTo('#showAnswer').css({
          'font-size': '90%',
          'margin': '0.2em auto'
        });
      }
    });
  }

});
