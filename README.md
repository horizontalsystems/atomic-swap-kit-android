## Overview

This is the android library that allows Atomic Swap, decentralized crypto currency exchange, between 2 parties. The implementation based on Hash Time Locked Contracts.


## Usage

### Initialization

The primary class to use is `SwapKit`.

```kotlin
val swapKit = SwapKit(context)

swapKit.registerSwapBlockchainCreator("BTC", BitcoinSwapBlockchainCreator(bitcoinKit))
swapKit.registerSwapBlockchainCreator("BCH", BitcoinSwapBlockchainCreator(bitcoinCashKit))

```

It takes `context` as constructor parameter. The supported coins should be registered to `SwapKit`.

### Exchanging crypto-curencies

There are 2 sides that take part in the process: Initiator and Responder. The process consists of the following steps:  

#### Request for a swap

Initiator creates swap request:

```kotlin
val swapRequest = swapKit.createSwapRequest(coinHave, coinWant, rate, amount)
```

#### Response to a swap

Responder creates response for this request:   

```kotlin
val swapResponse = swapKit.createSwapResponse(swapRequest)
```

Creating response also starts the swap process in the Responder side.

#### Initiate swap

Initiator takes response and starts the swap

```kotlin
swapKit.initiateSwap(parseFromResponseString(response))
```


## Prerequisites
* JDK >= 1.8
* Android 6 (minSdkVersion 23) or greater

## Installation
Add the JitPack to module build.gradle
```
repositories {
    maven { url 'https://jitpack.io' }
}
```
Add the following dependency to your build.gradle file:
```
dependencies {
    implementation 'com.github.horizontalsystems:atomic-swap-kit-android:master-SNAPSHOT'
}
```

## Example App

All features of the library are used in example project. It can be referred as a starting point for usage of the library.
* [Example App](https://github.com/horizontalsystems/atomic-swap-kit-android/tree/master/app)

## Dependencies
* [Bitcoin Kit Android](https://github.com/horizontalsystems/bitcoin-kit-android) - Full SPV wallet toolkit implementation for Bitcoin, Bitcoin Cash and Dash blockchains 

## License

The `Atomic Swap Kit` is open source and available under the terms of the [MIT License](https://github.com/horizontalsystems/atomic-swap-kit-android/blob/master/LICENSE)
