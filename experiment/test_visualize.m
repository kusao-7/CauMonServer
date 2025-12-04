% test_visualize.m
% 簡易テスト: visualize.m のレイアウト再構築動作を手動で確認するためのスクリプト
% 実行方法: MATLAB でこのファイルを開き、`test_visualize` を実行する。

function test_visualize()
    % 時間ベクトル
    t = linspace(0,2,21);

    % 共通のロバストネス/因果データ
    up_robM = zeros(size(t));
    low_robM = -zeros(size(t));
    up_optCau = [zeros(1,10), 100*ones(1,11)];
    low_optCau = -zeros(size(t));

    outfile = '';

    % 1) ウォームアップ: 2 シグナルで初回作成
    fprintf('\n=== Test 1: Warmup with 2 signals ===\n');
    trace2 = [t; 1+0*t; 0.5+0*t]; % 1 行目は time, 2つのシグナル
    visualize(trace2, 'phi_example', up_robM, low_robM, up_optCau, low_optCau, outfile, 'time,d_rel[t],v_ego[t]');
    pause(1);

    % 2) 更新: 同じ 2 シグナルで更新 (ウィンドウを再利用)
    fprintf('\n=== Test 2: Update with same 2 signals ===\n');
    trace2b = [t; 1+0.1*sin(2*pi*t); 0.5+0.1*cos(2*pi*t)];
    visualize(trace2b, 'phi_example', up_robM, low_robM, up_optCau, low_optCau, outfile, 'time,d_rel[t],v_ego[t]');
    pause(1);

    % 3) 再構築: 3 シグナルに増加 -> レイアウトを再構築して3つのシグナルを表示
    fprintf('\n=== Test 3: Rebuild with 3 signals ===\n');
    trace3 = [t; 1+0.1*sin(2*pi*t); 0.5+0.1*cos(2*pi*t); 1.2+0.05*randn(size(t))];
    visualize(trace3, 'phi_example', up_robM, low_robM, up_optCau, low_optCau, outfile, 'time,d_rel[t],v_ego[t],extra_signal');
    pause(1);

    % 4) 再度縮小: 2 シグナルに戻る -> 同じウィンドウをクリアして2シグナル用レイアウトへ
    fprintf('\n=== Test 4: Shrink back to 2 signals ===\n');
    visualize(trace2, 'phi_example', up_robM, low_robM, up_optCau, low_optCau, outfile, 'time,d_rel[t],v_ego[t]');
    pause(1);

    % 5) 4 シグナル以上: 5 シグナル入力 -> 3 つのみ表示されることを確認
    fprintf('\n=== Test 5: Input with 5 signals (only first 3 shown) ===\n');
    trace5 = [t; rand(4, length(t))*2 + 1];
    visualize(trace5, 'phi_example', up_robM, low_robM, up_optCau, low_optCau, outfile, 'time,a,b,c,d,e');
    pause(1);

    fprintf('\n=== Test complete ===\n');
end

