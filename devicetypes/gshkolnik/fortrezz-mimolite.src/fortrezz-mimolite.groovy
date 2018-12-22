/**
 *  FortrezZ Mimolite
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Based on Fortrezz Mimolite implementation
 */
metadata {
    definition (name: "FortrezZ MIMOlite", namespace: "gshkolnik", author: "FortrezZ, LLC") {
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        capability "Contact Sensor"
        capability "Voltage Measurement"

        attribute "powered", "string"

        command "on"
        command "off"

        fingerprint deviceId: "0x1000", inClusters: "0x72,0x86,0x71,0x30,0x31,0x35,0x70,0x85,0x25,0x03"
    }

    simulator {
    // Simulator stuff
    }

    preferences {
        input "RelaySwitchDelay", "decimal",
            title: "Delay between relay switch on and off in seconds. Only numbers 0 to 3.0 allowed. 0 value will remove delay and allow relay to function as a standard switch",
            description: "Numbers 0 to 3.1 allowed.",
            defaultValue: 0,
            required: false,
            displayDuringSetup: true

        input "SensorReportFreq", "decimal",
            title: "Periodic send interval of Multilevel Sensor Reports in the increment of 10 seconds. A value of 0 disables automatic reporting.",
            description: "Numbers greater than 0 allowed.",
            defaultValue: 0,
            required: false,
            displayDuringSetup: true

        input ("ToggleRelayOnSig", "bool", title: "Toggle relay when SIG1 is triggered?", options: ["Yes","No"])
    }


    // UI tile definitions
    tiles (scale: 2) {
        standardTile("switch", "device.switch", width: 4, height: 4, canChangeIcon: false, decoration: "flat") {
            state "on", label: "On", action: "off", icon: "st.switches.switch.on", backgroundColor: "#53a7c0"
            state "off", label: 'Off', action: "on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
        standardTile("contact", "device.contact", width: 2, height: 2, inactiveLabel: false) {
            state "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
            state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
        }
        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("powered", "device.powered", width: 2, height: 2, inactiveLabel: false) {
            state "powerOn", label: "Power On", icon: "st.switches.switch.on", backgroundColor: "#79b821"
            state "powerOff", label: "Power Off", icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
        }
        standardTile("configure", "device.configure", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        valueTile("voltage", "device.voltage", width: 2, height: 2) {
            state "val", label:'${currentValue}v', unit:"", defaultState: true
        }
        valueTile("voltageCounts", "device.voltageCounts", width: 2, height: 2) {
            state "val", label:'${currentValue}', unit:"", defaultState: true
        }
        main (["switch"])
        details(["switch", "contact", "voltage", "powered", "refresh","configure"])
    }
}

def parse(String description) {
    log.debug("parse: ${description}")

    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x84: 1, 0x30: 1, 0x70: 1, 0x31: 5])

    if (cmd) {
        result = zwaveEvent(cmd)
    }

    log.debug("Parse returned ${result} -- $cmd")

    return result
}

def updated() {
    configure()
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    log.debug("switchBinaryReport: ${cmd}")

    createEvent([name: "switch", value: cmd.value ? "on" : "off"])
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    // BasicSet is digital sensor for SIG1

    log.debug("BasicSet: ${cmd}")

    def contactEvent = createEvent([name: "contact", value: cmd.value ? "open" : "closed"])
    def cmds = [ ]

    log.debug("ToggleRelayOnSig: ${ToggleRelayOnSig}")
    if (ToggleRelayOnSig ?: false) {
        def toggleRelayResponse = null

        def switchState = device.currentState("switch").getValue()
        log.debug("switch state: ${switchState}")

        if (switchState == "off") {
            // turn the relay on
            log.debug("toggle relay to on")
            toggleRelayResponse = zwave.basicV1.basicSet(value: 0xFF).format()
        } else {
            // turn the relay off
            log.debug("toggle relay to off")
            toggleRelayResponse = zwave.basicV1.basicSet(value: 0x00).format()
        }

        cmds << toggleRelayResponse << "delay 100"
    }

    cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << "delay 100"
    cmds << zwave.sensorMultilevelV5.sensorMultilevelGet().format()

    [ contactEvent, response(cmds) ]
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
    // sensorBinaryReport is sent to Group 4 when SIG1 is triggered.
    // This should never be called because we remove the association with this group in configuration
    log.debug("sensorBinaryReport: ${cmd}")

    [:]
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    // sensorMultilevelReport is used to report the value of the analog voltage for SIG1

    log.debug("SensorMultilevelReport: ${cmd}")

    def adc = cmd.scaledSensorValue

    def voltage = "0";
    if (adc < 631) {
        voltage = "0"
    } else if (adc < 1179) {
        voltage = "0.5"
    } else if (adc < 1687) {
        voltage = "1"
    } else if (adc < 2062) {
        voltage = "1.5"
    } else if (adc < 2327) {
        voltage = "2"
    } else if (adc < 2510) {
        voltage = "2.5"
    } else if (adc < 2640) {
        voltage = "3"
    } else if (adc < 2741) {
        voltage = "3.5"
    } else if (adc < 2823) {
        voltage = "4"
    } else if (adc < 2892) {
        voltage = "4.5"
    } else if (adc < 2953) {
        voltage = "5"
    } else if (adc < 3004) {
        voltage = "5.5"
    } else if (adc < 3051) {
        voltage = "6"
    } else if (adc < 3093) {
        voltage = "6.5"
    } else if (adc < 3132) {
        voltage = "7"
    } else if (adc < 3167) {
        voltage = "7.5"
    } else if (adc < 3200) {
        voltage = "8"
    } else if (adc < 3231) {
        voltage = "8.5"
    } else if (adc < 3260) {
        voltage = "9"
    } else if (adc < 3286) {
        voltage = "9.5"
    } else if (adc < 3336) {
        voltage = "10"
    } else if (adc < 3380) {
        voltage = "11"
    } else if (adc < 3420) {
        voltage = "12"
    } else if (adc < 3458) {
        voltage = "13"
    } else if (adc < 3492) {
        voltage = "14"
    } else if (adc < 3523) {
        voltage = "15"
    } else {
        voltage = "EXCEEDS 16"
    }

    def voltageEvent = createEvent([name: "voltage", value: voltage, unit: "v"])
    def adcEvent = createEvent([name: "ADC Counts", value: adc])

    return ([voltageEvent, adcEvent])
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{
    log.debug("AlarmReport: ${cmd}")

    createEvent(name: "powered", value: "powerOff", descriptionText: "$device.displayName lost power")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    log.debug("Un-parsed Z-Wave message: ${cmd}")
    createEvent([:])
}   

def configure() {
    log.debug("configure")

    def switchDelay = RelaySwitchDelay == null ? 0 : (RelaySwitchDelay*10).toInteger()
    def reportFreq = SensorReportFreq == null ? 0 : SensorReportFreq.toInteger()

    delayBetween([
        // If a power dropout occurs, the MIMOlite will send an Alarm Command Class report (if there is enough available residual power)
        zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:[zwaveHubNodeId]).format(),
        // periodically send a multilevel sensor report of the ADC analog voltage to the input
        zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format(),
        // when the input is digitally triggered or untriggered, do send a binary sensor report - we already get BasicSet when SIG1 triggered
        zwave.associationV1.associationRemove(groupingIdentifier:4, nodeId:[zwaveHubNodeId]).format(),
        // Periodic send interval of Multilevel Sensor Reports (Association Group 2) and/or Pulse Count Reports (Association Group 5) for SIG1.
        // This parameter has a resolution of 10 seconds; for example, 1 = 10 seconds, 2 = 20 seconds, 3 = 30 seconds (Default).
        // A value of 0 disables automatic reporting.
        zwave.configurationV1.configurationSet(configurationValue: [reportFreq], parameterNumber: 9, size: 1).format(),
        // configurationValue for parameterNumber means how many 100ms do you want the relay
        // to wait before it cycles again / size should just be 1 (for 1 byte.)
        zwave.configurationV1.configurationSet(configurationValue: [switchDelay], parameterNumber: 11, size: 1).format()
    ])
}

def on() {
    log.debug("on")

    delayBetween([
        // physically changes the relay from off to on and requests a report of the relay
        zwave.basicV1.basicSet(value: 0xFF).format(),   
        // refresh to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
        refresh()
    ])
}

def off() {
    log.debug("off")

    delayBetween([
        // physically changes the relay from on to off and requests a report of the relay
        zwave.basicV1.basicSet(value: 0x00).format(),
        // refresh to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
        refresh()
    ])
}

def refresh() {
    log.debug("refresh")

    delayBetween([
        // requests a report of the relay to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
        zwave.switchBinaryV1.switchBinaryGet().format(),
        // requests a report of the analogue input voltage
        zwave.sensorMultilevelV5.sensorMultilevelGet().format()
    ])
}
