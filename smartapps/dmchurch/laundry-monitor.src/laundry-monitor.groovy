/**
 *  Copyright 2015 SmartThings
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
 *  Laundry Monitor
 *
 *  Author: SmartThings
 *
 *  Sends a message and (optionally) turns on or blinks a light to indicate that laundry is done.
 *
 *  Date: 2013-02-21
 */

definition(
	name: "Laundry Monitor",
	namespace: "dmchurch",
	author: "Danielle Church",
	description: "Sends a message and (optionally) turns on or blinks a light to indicate that laundry is done.",
	category: "Convenience",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/FunAndSocial/App-HotTubTuner.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/FunAndSocial/App-HotTubTuner%402x.png"
)

preferences {
    page(name: "prefsPage")
}

def prefsPage() {
    dynamicPage(name: "prefsPage", install: true, uninstall: true) {
        section("Tell me when these washer(s)/dryer(s) have stopped...") {
            input "washers", "capability.washerOperatingState", multiple: true, required: !dryers, title: "Washer(s)", submitOnChange: true
            input "dryers", "capability.dryerOperatingState", multiple: true, required: !washers, title: "Dryer(s)", submitOnChange: true
        }

        section("By turning on these lights") {
            input "switches", "capability.switch", required: true, multiple: true, title: "Which lights?"
            input "lightMode", "enum", options: ["Flash Lights", "Turn On Lights", "Lights While Running", "Lights While Stopped"], required: false, defaultValue: "Turn On Lights", title: "Action?"
        }

        section() {
            label(title: "Assign a name", required: false)
            mode(title: "Set for specific mode(s)")
        }
    }
}

def installed()
{
	log.trace "installed, initializing..."
	initialize()
}

def updated()
{
	log.trace "updated, reinitializing..."
	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "subscribing to washer state"
	subscribe(washers, "washerJobState", stateChangeHandler)
	log.debug "subscribing to dryer state"
	subscribe(dryers, "dryerJobState", stateChangeHandler)
    def running = isLaundryRunning()
    state.isRunning = running
    log.debug "init done; current state is $running"
}

def isDryerRunning(dryer) {
	return dryer.machineState == "run" && dryer.dryerJobState != "none" && dryer.dryerJobState != "finished" && dryer.dryerJobState != "wrinklePrevent"
}

def isWasherRunning(washer) {
	return washer.machineState == "run" && washer.washerJobState != "none" && washer.washerJobState != "finish"
}

def isLaundryRunning() {
    log.debug dryers?.currentDryerJobState
    log.debug washers?.currentWasherJobState
	//return dryers?.findAll(isDryerRunning) || washers.findAll(isWasherRunning)
    //return false
    return dryers?.currentDryerJobState.any{it && it != "none" && it != "finished" && it != "wrinklePrevent"} || washers?.currentWasherJobState.any{it && it != "none" && it != "finish"}
}

def stateChangeHandler(evt) {
	log.trace "stateChangeHandler called with $evt"
    def running = isLaundryRunning()
    def isRunning = !!state.isRunning
    log.trace "stateChange running: $running; isRunning: $isRunning"
    if (isRunning != running) {
    	log.debug "change app running state to $running"
        state.isRunning = running
        if (running) {
            notifyStart()
        } else {
        	notifyStop()
        }
    }
}

def notifyStart() {
	log.debug "notifying laundry started"
    if (lightMode?.equals("Lights While Running")) {
        log.debug "turning on lights"
        switches.on()
    } else if (lightMode?.equals("Lights While Stopped")) {
        log.debug "turning off lights"
        switches.off()
    }
}

def notifyStop() {
	log.debug "notifying laundry stopped"
    if (lightMode?.equals("Lights While Running")) {
        log.debug "turning off lights"
        switches.off()
    } else if (lightMode?.equals("Lights While Stopped")) {
        log.debug "turning on lights"
        switches.on()
    } else if (lightMode?.equals("Turn On Lights")) {
        log.debug "turning on lights"
        switches.on()
    } else {
        log.debug "flashing lights"
        flashLights()
    }
}

/*
def checkRunning() {
	log.trace "checkRunning()"
	if (state.isRunning) {
		def fillTimeMsec = fillTime ? fillTime * 60000 : 300000
		def sensorStates = sensor1.statesSince("acceleration", new Date((now() - fillTimeMsec) as Long))

		if (!sensorStates.find{it.value == "active"}) {

			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - fillTimeMsec > cycleTimeMsec) {
				log.debug "Sending notification"

				def msg = "${sensor1.displayName} is finished"
				log.info msg

                if (location.contactBookEnabled) {
                    sendNotificationToContacts(msg, recipients)
                }
                else {

                    if (phone) {
                        sendSms phone, msg
                    } else {
                        sendPush msg
                    }

                }

				if (switches) {
					if (lightMode?.equals("Turn On Lights")) {
						switches.on()
					} else {
						flashLights()
					}
				}
			} else {
				log.debug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
			}
			state.isRunning = false
			log.info "Disarming detector"
		} else {
			log.debug "skipping notification because vibration detected again"
		}
	}
	else {
		log.debug "machine no longer running"
	}
}
*/

private flashLights() {
	def doFlash = true
	def onFor = onFor ?: 1000
	def offFor = offFor ?: 1000
	def numFlashes = numFlashes ?: 3

	log.debug "LAST ACTIVATED IS: ${state.lastActivated}"
	if (state.lastActivated) {
		def elapsed = now() - state.lastActivated
		def sequenceTime = (numFlashes + 1) * (onFor + offFor)
		doFlash = elapsed > sequenceTime
		log.debug "DO FLASH: $doFlash, ELAPSED: $elapsed, LAST ACTIVATED: ${state.lastActivated}"
	}

	if (doFlash) {
		log.debug "FLASHING $numFlashes times"
		state.lastActivated = now()
		log.debug "LAST ACTIVATED SET TO: ${state.lastActivated}"
		def initialActionOn = switches.collect{it.currentSwitch != "on"}
		def delay = 1L
		numFlashes.times {
			log.trace "Switch on after  $delay msec"
			switches.eachWithIndex {s, i ->
				if (initialActionOn[i]) {
					s.on(delay: delay)
				}
				else {
					s.off(delay:delay)
				}
			}
			delay += onFor
			log.trace "Switch off after $delay msec"
			switches.eachWithIndex {s, i ->
				if (initialActionOn[i]) {
					s.off(delay: delay)
				}
				else {
					s.on(delay:delay)
				}
			}
			delay += offFor
		}
	}
}