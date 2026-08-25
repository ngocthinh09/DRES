import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { Overlay } from '@angular/cdk/overlay';
import { of } from 'rxjs';

import { RunViewerComponent } from './run-viewer.component';
import { AppConfig } from '../app.config';
import { EvaluationService } from '../../../openapi';

describe('RunViewerComponent HTTP polling', () => {
  let component: RunViewerComponent;
  let runService: jasmine.SpyObj<EvaluationService>;

  beforeEach(() => {
    runService = jasmine.createSpyObj('EvaluationService', [
      'getApiV2EvaluationByEvaluationIdState',
      'getApiV2EvaluationByEvaluationIdInfo',
    ]);
    runService.getApiV2EvaluationByEvaluationIdState.and.returnValue(of({ taskStatus: 'RUNNING', taskTemplateId: 't1' } as any));
    runService.getApiV2EvaluationByEvaluationIdInfo.and.returnValue(of({ name: 'Test Eval' } as any));

    TestBed.configureTestingModule({
      declarations: [RunViewerComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: EvaluationService, useValue: runService },
        { provide: ActivatedRoute, useValue: { params: of({ runId: 'eval-1' }), paramMap: of({ params: { runId: 'eval-1' }, get: () => 'eval-1' }) } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate'], { url: '/viewer/eval-1' }) },
        { provide: AppConfig, useValue: {} },
        { provide: MatSnackBar, useValue: jasmine.createSpyObj('MatSnackBar', ['open']) },
        { provide: Title, useValue: jasmine.createSpyObj('Title', ['setTitle']) },
        { provide: Overlay, useValue: {} },
        { provide: 'DOCUMENT', useValue: document },
      ],
    });
    component = TestBed.createComponent(RunViewerComponent).componentInstance;
  });

  it('polls the evaluation state immediately and every second', fakeAsync(() => {
    component.state.subscribe();
    tick();
    expect(runService.getApiV2EvaluationByEvaluationIdState).toHaveBeenCalledTimes(1);
    tick(1_000);
    expect(runService.getApiV2EvaluationByEvaluationIdState).toHaveBeenCalledTimes(2);
  }));
});
