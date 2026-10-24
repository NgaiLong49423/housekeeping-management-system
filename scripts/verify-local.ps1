# ResumeBookingId is retained only so old invocations fail with a clear retirement message.
param([long]$ResumeBookingId = 0)
$ErrorActionPreference = 'Stop'
throw 'This legacy workflow verifier is retired because it writes against the old schema. Use current tests and docs/demo-setup.md.'
