/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.dlv;

import com.goide.dlv.rdp.CompletionValueYetAnotherPoorFirefoxRdpStructure;
import com.goide.dlv.rdp.DlvRequest;
import com.goide.dlv.rdp.Grip;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.EvaluateContextBase;
import org.jetbrains.debugger.EvaluateResult;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.debugger.values.ValueManager;

import java.util.Map;

import static org.jetbrains.rpc.CommandProcessor.LOG;

public class DlvEvaluateContext extends EvaluateContextBase<DlvValueManager> {
  private final String frameActor;

  protected DlvEvaluateContext(@NotNull String frameActor, @NotNull DlvValueManager valueManager) {
    super(valueManager);

    this.frameActor = frameActor;
  }

  @NotNull
  @Override
  public Promise<EvaluateResult> evaluate(@NotNull String expression, @Nullable Map<String, Object> additionalContext, boolean enableBreak) {
    if (valueManager.isObsolete()) {
      return ValueManager.reject();
    }

    final AsyncPromise<CompletionValueYetAnotherPoorFirefoxRdpStructure>
      promise = new AsyncPromise<CompletionValueYetAnotherPoorFirefoxRdpStructure>();
    final DlvSuspendContextManager suspendContextManager = valueManager.getVm().getSuspendContextManager();
    valueManager.getVm().commandProcessor.send(DlvRequest.evaluate(valueManager.getVm().threadActor, expression, frameActor))
      .done(new Consumer<Void>() {
        @Override
        public void consume(Void aVoid) {
          LOG.assertTrue(suspendContextManager.clientEvaluate.compareAndSet(null, promise));
        }
      })
      .rejected(new Consumer<Throwable>() {
        @Override
        public void consume(Throwable e) {
          promise.setError(e);
        }
      });

    return promise.then(new Function<CompletionValueYetAnotherPoorFirefoxRdpStructure, EvaluateResult>() {
      @Override
      public EvaluateResult fun(CompletionValueYetAnotherPoorFirefoxRdpStructure result) {
        Value value;
        Grip exception = result.exception();
        if (exception == null) {
          value = valueManager.createValue(result);
        }
        else {
          value = valueManager.createValue(exception, null, null);
        }
        return new EvaluateResult(value, exception != null);
      }
    });
  }
}