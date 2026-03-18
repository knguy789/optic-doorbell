#include <Servo.h>

Servo myServo;
const int buttonPin = 2;    // Pin connected to the button
const int servoPin = 3;     // Pin connected to the servo signal
int buttonState = 0;

int e = 329.63;
int c_sharp = 277.18;

void setup()
{
  pinMode(4, OUTPUT);
  pinMode(7, INPUT_PULLUP);
  myServo.attach(servoPin);
  pinMode(buttonPin, INPUT_PULLUP); // Uses internal resistor
  myServo.write(0);                 // Set initial position
}

void loop()
{
  buttonState = digitalRead(buttonPin);
  if (!digitalRead(7))
  {
    tone(4, e);
    delay(1000);
    tone(4, c_sharp);
    delay(1000);
    noTone(4);
    delay(1500);
    tone(4, e);
    delay(1000);
    tone(4, c_sharp);
    delay(1000);
    noTone(4);
  }
  if (buttonState == LOW) {         // If button is pressed (LOW)
    myServo.write(90);             // Move to 180 degrees
  } else {
    myServo.write(0);               // Return to 0 degrees
  }
  delay(15);
}
