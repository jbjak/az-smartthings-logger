/**
 *  Azure Queues
 *
 *  Copyrigth 2020 Jeff Schnurr
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
definition(
    name: 'ST-Queue',
    namespace: 'jschnurr',
    author: 'Jeff Schnurr',
    description: 'Smartthings Azure Queue Integration',
    category: 'My Apps',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
    iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png') {
    appSetting 'StorageAccount'
    appSetting 'Queue'
    appSetting 'SASToken',  // allowed services: Queue, Allowed Resource Types: Object, Allowed Permissions: Add
    }

preferences {
    section('Power Meter') {
        input 'power', 'capability.powerMeter', title: 'Power Sensor', multiple: true, required: false
    }
    section('Environment') {
        input 'thermOperatingStates', 'capability.thermostat', title: 'Therm Operating States', multiple: true, required: false
        input 'temperatures', 'capability.temperatureMeasurement', title: 'Temperature Sensors', multiple: true, required: false
    }
    section('Security') {
        input 'contacts', 'capability.contactSensor', title: 'Contact Sensors', multiple: true, required: false
        input 'motions', 'capability.motionSensor', title: 'Motion Sensors', multiple: true, required: false
        input 'locks', 'capability.lock', title: 'Locks', multiple: true, required: false
    }
    section('Switches') {
        input 'switches', 'capability.switch', title: 'Switches', multiple: true, required: false
        input 'dimmerSwitches', 'capability.switchLevel', title: 'Dimmer Switches', required: false, multiple: true
    }
    section('Log Other Devices') {
        input 'acceleration', 'capability.accelerationSensor', title: 'Acceleration Sensors', multiple: true, required: false
        input 'alarm', 'capability.alarm', title: 'Alarm', required: false, multiple: true
        input 'batteries', 'capability.battery', title: 'Batteries', multiple: true, required: false
        input 'beacon', 'capability.beacon', title: 'Beacon', required: false, multiple: true
        input 'button', 'capability.button', title: 'Buttons', multiple: true, required: false
        input 'colorControl', 'capability.colorControl', title: 'Color Control', multiple: true, required: false
        input 'humidities', 'capability.relativeHumidityMeasurement', title: 'Humidity Sensors', required: false, multiple: true
        input 'illuminances', 'capability.illuminanceMeasurement', title: 'Illuminance Sensors', required: false, multiple: true
        input 'presenceSensors', 'capability.presenceSensor', title: 'Presence Sensors', required: false, multiple: true
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    // Power
    subscribe(power, 'power', handlePowerEvent)

    // Environment
    subscribe(temperatures, 'temperature', handleEnvironmentEvent)
    subscribe(humidities, 'humidity', handleEnvironmentEvent)
    subscribe(thermOperatingStates, 'thermostatOperatingState', handleEnvironmentEvent)

    // Security
    subscribe(contacts, 'contact', handleSecurityEvent)
    subscribe(locks, 'lock', handleSecurityEvent)
    subscribe(motions, 'motion', handleSecurityEvent)
    subscribe(alarm, 'alarm', handleSecurityEvent)

    // Switches
    subscribe(switches, 'switch', handleSwitchEvent)
    subscribe(dimmerSwitches, 'level', handleSwitchEvent)
    subscribe(dimmerSwitches, 'switch', handleSwitchEvent)

    // Other
    subscribe(acceleration, 'acceleration', handleOtherEvent)
    subscribe(batteries, 'battery', handleOtherEvent)
    subscribe(beacon, 'beacon', handleOtherEvent)
    subscribe(button, 'button', handleOtherEvent)
    subscribe(colorControl, 'Color Control', handleOtherEvent)
    subscribe(illuminances, 'illuminance', handleOtherEvent)
    subscribe(presenceSensors, 'presence', handleOtherEvent)
}

def sendEvent(evt, sensorType) {
    def now = new Date().format('yyyyMMdd-HH:mm:ss.SSS', TimeZone.getTimeZone('UTC'))
    def payload = buildEventMessage(evt, sensorType)
    log.debug "Sending AzureQ event payload: ${payload}"
    def params = [
        uri: "https://${appSettings.StorageAccount}.queue.core.windows.net/${appSettings.Queue}/messages${appSettings.SASToken}",
        body: "<QueueMessage><MessageText>${payload}</MessageText></QueueMessage>",
        contentType: 'application/xml; charset=utf-8',
        requestContentType: 'application/atom+xml;type=entry;charset=utf-8',
        headers: ['x-ms-date': now],
    ]

    try {
        httpPost(params) { resp ->
            log.debug "response message ${resp}"
        }
    } catch (e) {
        // successful creates come back as 200, so filter for 'Created' and throw anything else
        if (e.toString() != 'groovyx.net.http.ResponseParseException: Created') {
            log.error "Error sending event: $e"
            throw e
        }
    }
}

private buildEventMessage(evt, sensorType) {
    def payload = [
        date: evt.isoDate,
        hub: evt.hubId,
        deviceId: evt.deviceId,
        deviceType: sensorType,
        eventId: evt.id,
        device: evt.displayName.trim(),
        property: evt.name.trim(),
        value: evt.value,
        unit: evt.unit,
        isphysical: evt.isPhysical(),
        isstatechange: evt.isStateChange(),
        source: evt.source,
        location: evt.location,
  ]
    def attribs = ''
    def sep = ''
    payload.each { k, v ->
        k = k.replaceAll('"', '\"')
        v = "${v}".replaceAll('"', '\"')
        attribs += "${sep}\"${k}\":\"${v}\""
        sep = ','
    }
    def jsonstr = '{' + attribs + '}'
    return jsonstr
}

private handlePowerEvent (evt) {
    sendEvent(evt, 'power')
}

private handleEnvironmentEvent (evt) {
    sendEvent(evt, 'environment')
}

private handleSecurityEvent (evt) {
    sendEvent(evt, 'security')
}

private handleSwitchEvent (evt) {
    sendEvent(evt, 'switch')
}

private handleOtherEvent (evt) {
    sendEvent(evt, 'other')
}
