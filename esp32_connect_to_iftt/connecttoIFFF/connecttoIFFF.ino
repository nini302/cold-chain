#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>

#include "MAX31855.h"
HTTPClient http;
#include <Arduino.h>
#include <U8g2lib.h>
U8G2_SSD1306_128X32_UNIVISION_F_HW_I2C u8g2(U8G2_R0,16,5,4);
#ifdef U8X8_HAVE_HW_SPI
#include <SPI.h>
#endif
#ifdef U8X8_HAVE_HW_I2C
#include <Wire.h>
#endif
MAX31855 tc;
//char* ssid = "2209202-PC 2636";
//char* password = "12345678";

char* ssid = "whoops27";
char* password = "01260714";
const char* host="maker.ifttt.com";
const uint16_t port=80;
float sensordata1=0.000;
float sensordata2=0.000;
float sensordata3=0.000;
int count_id=0;
int filterN=50;
float vale[51];
float analog[51];
int i=0;
const int analogInPin = A0; 
const int doPin = 12;
const int csPin = 15;
const int clPin = 14;
//====================================================================
struct sensordata{
  float a,b,c;
  };
void setup() {
  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password); //--> Connect to your WiFi router
  Serial.println("");
  Serial.print("Connecting");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(100);}
  Serial.println("");
  Serial.print("Successfully connected to : ");
  Serial.println(ssid);
  Serial.print("NodeMCU IP address : ");
  Serial.println(WiFi.localIP());
  http.setReuse(true);
  u8g2.begin();
  drawtwostring("Connected to WiFi ",ssid);
  tc.begin(clPin,csPin,doPin); 

  
  
}

void loop() {
  // put your main code here, to run repeatedly:
  
  sensordata r;
  for(int k=0;k<52;k++){
  r=temperature();}
  //send_to_http(r.a,r.b,r.c);
  //yield();
  delay(1000);
  
}
sensordata temperature() 
{ 
  sensordata s;
  int status=tc.read();
  float ss=tc.getTemperature();
  if(count_id>filterN)
  {
    count_id=0;
  }
  vale[count_id]= (float)tc.getTemperature();
  count_id++;
  //Serial.println(ss);
//  Serial.println(vale[1]);
  float temp,sum=0;
  for(int j=0;j<filterN;j++){
    sum=sum+vale[j];
//    Serial.print(val[j]);
//    Serial.print(",");
    }
  
  temp=(sum/filterN)+0.3;
    
  Serial.print("temperature:\t");
//  Serial.println(sum);
  Serial.println(temp+1, 5);
  i=i+1;
  if(i>filterN-2){
  i=1;
  }
  analog[count_id]=analogRead(analogInPin);
//  Serial.print(analog[count_id],2);
//  Serial.print("  ");
  float analogval,analogsum=0;
  for(int k=0;k<filterN;k++){
    analogsum=analogsum+analog[k];
    }
  analogval=analogsum/filterN;
//  Serial.print(analogval);
  float Vout=analogval*54/1024;
//  Serial.print("  ");
  Serial.print(Vout);
  float VR=Vout/(5-Vout);
  Serial.print("   VR: ");
  Serial.println(VR);
  s.a=temp;
  s.b=Vout;
  s.c=VR;
  return s;
}

//========================================================================
void drawstring(char *input_word,int x,int y){
  u8g2.clearBuffer();          // clear the internal memory
  u8g2.setFont(u8g2_font_ncenB08_tr); // choose a suitable font
  u8g2.drawStr(x,y,input_word);  // write something to the internal memory
  u8g2.sendBuffer();
  delay(100);
  
}
void drawtwostring(char *firstrow,char *secondrow){
  u8g2.clearBuffer();          // clear the internal memory
  u8g2.setFont(u8g2_font_ncenB08_tr); // choose a suitable font
  u8g2.drawStr(0,10,firstrow);  // write something to the internal memory
  u8g2.drawStr(0,20,secondrow);
  u8g2.sendBuffer();
  delay(100);
  }

void send_to_http(float a,float b,float c){
  // Use WiFiClient class to create TCP connections
    WiFiClient client;
    if (!client.connect(host, port)) {
      Serial.println("connection failed");
      delay(500);
      return;
    }
  
    // This will send a string to the server
    Serial.println("sending data to server");
    String stringa=String((float)a,3);
    String stringb=String((float)b,3);
    String stringc=String((float)c,3);
    if (client.connected()) {
        String input_word ="https://maker.ifttt.com/trigger/temperature_sensor/with/key/d-ms8paxRMm0VxTE3AT_H9";
        input_word+="?value1=";
        input_word+=stringa;
        input_word+="&value2=";
        input_word+=stringb;
        input_word+="&value3=";
        input_word+=stringc;
        http.begin(client, input_word);
        //http.begin(client, "jigsaw.w3.org", 80, "/HTTP/connection.html");

        int httpCode = http.GET();
        if (httpCode > 0) {
          Serial.printf("[HTTP] GET... code: %d\n", httpCode);
    
          // file found at server
          if (httpCode == HTTP_CODE_OK) {
            http.writeToStream(&Serial);
          }
        } else {
          Serial.printf("[HTTP] GET... failed, error: %s\n", http.errorToString(httpCode).c_str());
        }
        if(httpCode==200){
          delay(100);
          //for(int k=0;k<60;k++){
          //delay(100);
          //}
          }
        http.end();
    }
    // wait for data to be available
    unsigned long timeout = millis();
    while (client.available() == 0) {
      if (millis() - timeout > 5000) {
        Serial.println(">>> Client Timeout !");
        client.stop();
        delay(1000);
        return;
      }
    }
  
    // Read all the lines of the reply from server and print them to Serial
    Serial.println("receiving from remote server");
    // not testing 'client.connected()' since we do not need to send data here
    while (client.available()) {
      char ch = static_cast<char>(client.read());
      Serial.print(ch);
    }
    client.stop();
}
