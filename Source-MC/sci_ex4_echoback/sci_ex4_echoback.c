//#############################################################################
//
// FILE:   sci_ex4_echoback.c
//
// TITLE:  SCI echoback example.
//
//! \addtogroup driver_example_list
//! <h1>SCI Echoback</h1>
//!
//!  This test receives and echo-backs data through the SCI-A port.
//!
//!  A terminal such as 'putty' can be used to view the data from
//!  the SCI and to send information to the SCI. Characters received
//!  by the SCI port are sent back to the host.
//!
//!  \b Running \b the \b Application
//!  Open a COM port with the following settings using a terminal:
//!  -  Find correct COM port
//!  -  Bits per second = 9600
//!  -  Data Bits = 8
//!  -  Parity = None
//!  -  Stop Bits = 1
//!  -  Hardware Control = None
//!
//!  The program will print out a greeting and then ask you to
//!  enter a character which it will echo back to the terminal.
//!
//!  \b Watch \b Variables \n
//!  - loopCounter - the number of characters sent
//!
//! \b External \b Connections \n
//!  Connect the SCI-A port to a PC via a transceiver and cable.
//!  - GPIO28 is SCI_A-RXD (Connect to Pin3, PC-TX, of serial DB9 cable)
//!  - GPIO29 is SCI_A-TXD (Connect to Pin2, PC-RX, of serial DB9 cable)
//
//#############################################################################
//
//
// $Copyright:
// Copyright (C) 2021 Texas Instruments Incorporated - http://www.ti.com/
//
// Redistribution and use in source and binary forms, with or without 
// modification, are permitted provided that the following conditions 
// are met:
// 
//   Redistributions of source code must retain the above copyright 
//   notice, this list of conditions and the following disclaimer.
// 
//   Redistributions in binary form must reproduce the above copyright
//   notice, this list of conditions and the following disclaimer in the 
//   documentation and/or other materials provided with the   
//   distribution.
// 
//   Neither the name of Texas Instruments Incorporated nor the names of
//   its contributors may be used to endorse or promote products derived
//   from this software without specific prior written permission.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT 
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// $
//#############################################################################

//
// Included Files
//
#include "driverlib.h"
#include "device.h"
#include<stdlib.h>
//
// Defines
//
// Define AUTOBAUD to use the autobaud lock feature
//#define AUTOBAUD

//
// Globals
//

typedef union _Float{
    float f;
    unsigned long bytes;
} Float;
struct string{
    char * arr;
    int sz;
    int ptr;
};

typedef struct string string;


string * new_string(){
    string * ret = malloc(1*sizeof(string));
    ret->sz = 1;
    ret->ptr = 0;
    ret->arr = (char *) malloc(2*sizeof(char));
    return ret;
}

void append(string * str, char c){
    if(str->ptr == str-> sz){
        str->sz *= 2;
        str->arr = (char *) realloc(str->arr, str->sz + 1);
    }
    str->arr[str->ptr++] = c;
    str->arr[str->ptr] = 0;
}

// represent each byte in the byte arr in its integer value
// and append to the intarr
void byteArrayToIntArray(unsigned char * bytearr, unsigned  char * intarr){
    int i = 3, j = 15;
    for(; i>-1; i--){
        unsigned int x = (unsigned int)bytearr[i];
        int k = 0;
        for(; k<3; k++){
            intarr[j--] = (unsigned int)'0' + x%10;
            x /= 10;
        }
        intarr[j--] = '.';
    }
}

void SCI_readFloatBlockingFIFO(unsigned char * intarr, Float * receivedValue){
    int i = 0;
    unsigned char byte[4];
    uint16_t rxStatus = 0U;
    for(; i<4; i++){
        byte[i] = SCI_readCharBlockingFIFO(SCIA_BASE);
        rxStatus = SCI_getRxStatus(SCIA_BASE);
        if((rxStatus & SCI_RXSTATUS_ERROR) != 0){
            ESTOP0;
        }
    }

    byteArrayToIntArray(byte, intarr);
    receivedValue->bytes = ((unsigned long)byte[0]<<24 | (unsigned long)byte[1] << 16 | (unsigned long)byte[2] << 8 | (unsigned long)byte[3]);
}

void floatToDigits(unsigned char * arr, Float * receivedValue){
    float x = receivedValue->f;
    if(x < 0) x*=-1;
    if(x==0) x = 123;
    while(x<100){
        x*=10;
    }
   unsigned int xi = x;
   int i = 4;
   while(i--){
       arr[i] = '0' + xi%10;
//       arr[i] = '1' + 1;
       xi/=10;
   }
}
//
// Main
//
//float receivedValue = 1.24;''
void main(void)
{
//
//    unsigned char *msg = "You Sent : ";
//

    //
    // Configure PLL, disable WD, enable peripheral clocks.
    //
    Device_init();

    //
    // Disable pin locks and enable internal pullups.
    //
    Device_initGPIO();

    //
    // GPIO28 is the SCI Rx pin.
    //
    GPIO_setMasterCore(DEVICE_GPIO_PIN_SCIRXDA, GPIO_CORE_CPU1);
    GPIO_setPinConfig(DEVICE_GPIO_CFG_SCIRXDA);
    GPIO_setDirectionMode(DEVICE_GPIO_PIN_SCIRXDA, GPIO_DIR_MODE_IN);
    GPIO_setPadConfig(DEVICE_GPIO_PIN_SCIRXDA, GPIO_PIN_TYPE_STD);
    GPIO_setQualificationMode(DEVICE_GPIO_PIN_SCIRXDA, GPIO_QUAL_ASYNC);

    //
    // GPIO29 is the SCI Tx pin.
    //
    GPIO_setMasterCore(DEVICE_GPIO_PIN_SCITXDA, GPIO_CORE_CPU1);
    GPIO_setPinConfig(DEVICE_GPIO_CFG_SCITXDA);
    GPIO_setDirectionMode(DEVICE_GPIO_PIN_SCITXDA, GPIO_DIR_MODE_OUT);
    GPIO_setPadConfig(DEVICE_GPIO_PIN_SCITXDA, GPIO_PIN_TYPE_STD);
    GPIO_setQualificationMode(DEVICE_GPIO_PIN_SCITXDA, GPIO_QUAL_ASYNC);

    //
    // Initialize interrupt controller and vector table.
    //
    Interrupt_initModule();
    Interrupt_initVectorTable();

    //
    // Initialize SCIA and its FIFO.
    //
    SCI_performSoftwareReset(SCIA_BASE);

    //
    // Configure SCIA for echoback.
    //
    SCI_setConfig(SCIA_BASE, DEVICE_LSPCLK_FREQ, 9600, (SCI_CONFIG_WLEN_8 |
                                                        SCI_CONFIG_STOP_ONE |
                                                        SCI_CONFIG_PAR_NONE));
    SCI_resetChannels(SCIA_BASE);
    SCI_resetRxFIFO(SCIA_BASE);
    SCI_resetTxFIFO(SCIA_BASE);
    SCI_clearInterruptStatus(SCIA_BASE, SCI_INT_TXFF | SCI_INT_RXFF);
    SCI_enableFIFO(SCIA_BASE);
    SCI_enableModule(SCIA_BASE);
    SCI_performSoftwareReset(SCIA_BASE);

#ifdef AUTOBAUD
    //
    // Perform an autobaud lock.
    // SCI expects an 'a' or 'A' to lock the baud rate.
    //
    SCI_lockAutobaud(SCIA_BASE);
#endif

    Float receivedValue;
    unsigned char arr[4];
    unsigned char intarr[16];
    while(1){
        SCI_readFloatBlockingFIFO(intarr, &receivedValue);
        floatToDigits(arr, &receivedValue);
//        SCI_writeCharArray(SCIA_BASE, (uint16_t*)intarr, 16);
        SCI_writeCharArray(SCIA_BASE, (uint16_t*)arr, 4);
    }





        //
        // Echo back the characters.


//        SCI_writeCharBlockingFIFO(SCIA_BASE, receivedChar);



}

//
// End of File
//

