<script language="JavaScript" >
  function myrefresh(){
    window.location.reload();
  }
  setTimeout('submitForm()',1000); 
</script>

<script>
  
  document.addEventlistener("DOMContentLoaded",function(){
    google.script.run.withSuccessHandler(generateTable).getTableData();
  });
  function generateTable(dataArray){
    var tbody=document.getElementById("table-body");
    dataArray.forEach(function(r){
      var row=document.createElement("tr");
      var col1=document.createElement("td");
      col1.textContent=r[0];
      var col2=document.createElement("td");
      col2.textContent=r[1];
      var col3=document.createElement("td");
      col3.textContent=r[2];
      row.append(col1);
      row.append(col2);
      row.append(col3);
      tbody.append(row);
    })
  }
</script>