$(function () {
  var time;
  
  var start = function(){
    time = setInterval(function () {
      $.ajax({
        type: 'get',
        url: '/VideoMeeting/rtpServer/api/getAllInfo',
        dataType: 'json',
        data: '',
        success: function (data) {
          console.log(data)
          var tableHtml = template('tableTemplate', {allInfo: data});
          $('#container').html(tableHtml);
        }
      })
    }, 2000)
  };

  start();
  
  var stop = function () {
    clearInterval(time)
  }

  $('#btn').on('click', function () {
    if(this.innerHTML === '暂停'){
      stop();
      this.innerHTML = '开始';
    }else{
      start();
      this.innerHTML = '暂停';
    }
  })
})