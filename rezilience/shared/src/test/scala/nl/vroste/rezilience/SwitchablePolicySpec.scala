package nl.vroste.rezilience

import nl.vroste.rezilience.Policy.WrappedError
import zio.test.Assertion.{ equalTo, fails }
import zio.test.TestAspect.{ nonFlaky, timed, timeout }
import zio.test._
import zio.{ durationInt, Promise, Ref, ZIO }

object SwitchablePolicySpec extends ZIOSpecDefault {

  override def spec = suite("switchable policy")(
    suite("transition mode")(
      test("uses the new policy after switching") {
        val initialPolicy = Retry.make().map(_.toPolicy)

        ZIO.scoped {
          for {
            callWithPolicy <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            e              <- failFirstTime
            _              <- callWithPolicy(e)       // Should succeed
            _              <- callWithPolicy.switch(ZIO.succeed(Policy.noop))
            e2             <- failFirstTime
            result         <- callWithPolicy(e2).exit // Should fail
          } yield assert(result)(fails(equalTo(WrappedError(()))))
        }
      },
      test("does not wait for in-flight calls to finish when switching") {
        val initialPolicy = Bulkhead.make(1).map(_.toPolicy)

        ZIO.scoped {
          for {
            callWithPolicy                 <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            waitForLatch                   <- waitForLatch
            WaitForLatch(e, started, latch) = waitForLatch
            fib                            <- callWithPolicy(e).fork
            _                              <- started.await
            fib2                           <- callWithPolicy(e).fork
            _                              <- callWithPolicy.switch(ZIO.succeed(Policy.noop))
            _                              <- callWithPolicy(ZIO.unit) // Should return immediately with the new noop policy
            _                              <- latch.succeed(())
            _                              <- fib.join
            _                              <- fib2.join
          } yield assertCompletes
        }

      },
      test("in-flight calls can be interrupted while switching") {
        val initialPolicy = Bulkhead.make(1).map(_.toPolicy)

        ZIO.scoped {
          for {
            callWithPolicy                 <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            waitForLatch                   <- waitForLatch
            WaitForLatch(e, started, latch) = waitForLatch
            fib                            <- callWithPolicy(e).fork
            _                              <- started.await
            fib2                           <- callWithPolicy(e).fork
            oldPolicyReleased              <- callWithPolicy.switch(ZIO.succeed(Policy.noop))
            _                              <- fib.interrupt
            _                              <- fib2.interrupt
            _                              <- oldPolicyReleased
            _                              <- callWithPolicy(ZIO.unit) // Should return immediately with the new noop policy
          } yield assertCompletes
        }
      }
    ),
    suite("finish in flight mode")(
      test("uses the new policy after switching") {
        val initialPolicy = Retry.make().map(_.toPolicy)

        val failFirstTime: ZIO[Any, Nothing, ZIO[Any, Unit, Unit]] = for {
          ref   <- Ref.make(0)
          effect = ref
                     .getAndUpdate(_ + 1)
                     .flatMap(count => ZIO.fail(()).when(count < 1))
        } yield effect.unit

        ZIO.scoped {
          for {
            callWithPolicy <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            e              <- failFirstTime
            _              <- callWithPolicy(e)       // Should succeed
            _              <- callWithPolicy.switch(ZIO.succeed(Policy.noop), SwitchablePolicy.Mode.FinishInFlight)
            e2             <- failFirstTime
            result         <- callWithPolicy(e2).exit // Should fail
          } yield assert(result)(fails(equalTo(WrappedError(()))))
        }
      },
      test("waits for in-flight calls to finish when switching") {
        val initialPolicy = Bulkhead.make(1).map(_.toPolicy)

        ZIO.scoped {
          for {
            callWithPolicy                 <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            waitForLatch                   <- waitForLatch
            WaitForLatch(e, started, latch) = waitForLatch
            fib                            <- callWithPolicy(e).fork
            _                              <- started.await
            fib2                           <- callWithPolicy(e).fork
            _                              <- callWithPolicy.switch(ZIO.succeed(Policy.noop), SwitchablePolicy.Mode.FinishInFlight)
            callWithNewPolicySucceeded     <- Promise.make[Nothing, Unit]
            fib3                           <- callWithPolicy(
                                                ZIO.fail("Effect was executed before latch closed").unlessZIO(latch.isDone) *>
                                                  callWithNewPolicySucceeded.succeed(())
                                              ).fork // Should wait until the latch
            _ <- latch.succeed(())
            _ <- fib3.join
            _ <- fib.join
            _ <- fib2.join
          } yield assertCompletes
        }
      },
      test("in-flight calls can be interrupted while switching") {
        val initialPolicy = Bulkhead.make(1).map(_.toPolicy)

        ZIO.scoped {
          for {
            callWithPolicy                 <- SwitchablePolicy.make[Any, Nothing, Any](initialPolicy)
            waitForLatch                   <- waitForLatch
            WaitForLatch(e, started, latch) = waitForLatch
            fib                            <- callWithPolicy(e).fork
            _                              <- started.await
            fib2                           <- callWithPolicy(e).fork
            released                       <- callWithPolicy.switch(ZIO.succeed(Policy.noop), SwitchablePolicy.Mode.FinishInFlight)
            callWithNewPolicySucceeded     <- Promise.make[Nothing, Unit]
            fib3                           <- callWithPolicy(callWithNewPolicySucceeded.succeed(())).fork // Should wait until the latch
            _                              <- fib.interrupt
            _                              <- fib2.interrupt
            _                              <- released
            _                              <- latch.succeed(())
            _                              <- fib3.join
          } yield assertCompletes
        }
      }
    )
  ) @@ timed @@ timeout(60.seconds) @@ nonFlaky

  case class WaitForLatch(
    effect: ZIO[Any, Nothing, Unit],
    started: Promise[Nothing, Unit],
    latch: Promise[Nothing, Unit]
  )

  val waitForLatch: ZIO[Any, Nothing, WaitForLatch] = for {
    latch   <- Promise.make[Nothing, Unit]
    started <- Promise.make[Nothing, Unit]
    effect   = started.succeed(()) *> latch.await
  } yield WaitForLatch(effect, started, latch)

  val failFirstTime: ZIO[Any, Nothing, ZIO[Any, Unit, Unit]] = for {
    ref   <- Ref.make(0)
    effect = ref.getAndUpdate(_ + 1).flatMap(count => ZIO.fail(()).when(count < 1))
  } yield effect.unit

  // TODO add tests for edge-case behavior, eg interruption
}
