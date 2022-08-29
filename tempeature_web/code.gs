
var url='https://docs.google.com/spreadsheets/d/1nMnu-mxjAvdZ-g9wA2Fy78dQkfx3HksBWFPQgzavQ_Y/edit#gid=0';
function doGet(){
  var x = HtmlService.createTemplateFromFile("index");
  var y = x.evaluate();
  var z= y.setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  return z;
}

function getTableData(){
  var a=SpreadsheetApp.openByUrl(url);
  var b=a.getSheetByName("table");
  var c=b.getRange(b.getLastRow(),3,1,3);
  var data= c.getValues();
  return data;
}

function include(filename) {
  return HtmlService.createHtmlOutputFromFile(filename).getContent();
}