function visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, outfile, signal_names)
%PLOT_STL_ANALYSIS Generic plotting routine for STL analysis results
%
% [動的更新バージョン]
% 1つのFigureウィンドウを再利用し、プロットデータのみを更新する。
%
% 引数:
%   signal_names: カンマ区切りのシグナル名文字列 (例: 'temp,cooling')

    % --- 引数のデフォルト値設定 ---
    if nargin < 7
        outfile = 'Figure_output.png'; % 保存ファイル名（毎回上書き）
    end
    if nargin < 8 || isempty(signal_names)
        signal_names = ''; % デフォルトは空
    end

    % --- データ抽出 (時間とシグナル数) ---
    t = trace(1,:);
    num_signals = size(trace,1) - 1;

    % --- シグナル名をパース ---
    if ~isempty(signal_names)
        signal_name_list = strsplit(signal_names, ',');
        % 前後の空白を削除
        signal_name_list = strtrim(signal_name_list);

        % --- 先頭に time が含まれている場合は取り除く ---
        % trace の 1 行目が time なので、signal_names に time が入っていると
        % プロットと名前の対応がずれてしまう。ユーザーは time を省略してよい。
        if ~isempty(signal_name_list)
            % 条件: 名前配列の長さが期待するシグナル数+1（time を含む）
            % または先頭が 'time'/'t' と明示的に書かれている場合
            if length(signal_name_list) == num_signals + 1 || strcmpi(signal_name_list{1}, 'time') || strcmpi(signal_name_list{1}, 't')
                signal_name_list = signal_name_list(2:end);
            end
        end
    else
        % デフォルトのシグナル名
        signal_name_list = cell(1, num_signals);
        for s = 1:num_signals
            signal_name_list{s} = sprintf('Signal %d', s);
        end
    end

    % --- デバッグ情報出力 ---
    fprintf('\n=== DEBUG INFO ===\n');
    fprintf('Original signal names string: %s\n', signal_names);
    fprintf('Processed signal name list: %s\n', strjoin(signal_name_list, ','));
    fprintf('Number of signals: %d\n', num_signals);
    fprintf('Time array length: %d\n', length(t));
    fprintf('up_robM length: %d\n', length(up_robM));
    fprintf('low_robM length: %d\n', length(low_robM));
    fprintf('up_optCau length: %d\n', length(up_optCau));
    fprintf('low_optCau length: %d\n', length(low_optCau));
    fprintf('Time values: [%s]\n', num2str(t, '%.1f '));
    fprintf('up_robM values: [%s]\n', num2str(up_robM, '%.2f '));
    fprintf('low_robM values: [%s]\n', num2str(low_robM, '%.2f '));
    fprintf('==================\n\n');

    % --- 永続的なFigureハンドルを検索 ---
    % 'CauMon_Realtime_Monitor_Figure' という名前のタグで既存のFigureを探す
    figTag = 'CauMon_Realtime_Monitor_Figure';
    figHandle = findobj('Tag', figTag);

    if isempty(figHandle)
        % --- 初回呼び出し (Figureが存在しない場合) ---
        % グラフを新規作成し、プロットのハンドルを保存する。

        total_plots = num_signals + 2; % シグナル数 + ロバストネス + 因果関係

        % ★ Figureに 'Tag' を追加して作成
        figHandle = figure('Tag', figTag, 'Position',[100 100 900 300 + 150*total_plots]);

        tiledlayout(total_plots,1, 'Padding','compact', 'TileSpacing','compact');

        % ハンドルを保存するための構造体
        handles = struct();

        % ====== (1..N) 各シグナルをプロット ======
        handles.h_sig = gobjects(num_signals, 1); % シグナルプロット用ハンドル配列
        handles.ax_sig = gobjects(num_signals, 1); % シグナル軸用ハンドル配列
        for s = 1:num_signals
            handles.ax_sig(s) = nexttile; % ★ 軸ハンドルを保存

            % ★ プロットハンドルを h_sig に保存 (インデックスは s)
            handles.h_sig(s) = plot(t, trace(s+1,:), 'LineWidth', 2); % s+1 行目がシグナルデータ

            % ★ シグナル名を使用して縦軸ラベルを設定（タイトルは表示しない）
            if s <= length(signal_name_list)
                sig_name = signal_name_list{s};
                % title を削除（不要）
                ylabel(sig_name, 'Interpreter', 'none');
            else
                % title を削除（不要）
                ylabel('Value');
            end
            xlabel('time');
            grid on;
            set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        end

        % ====== (N+1) ロバストネスをプロット ======
        handles.ax_rob = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        % データ長が一致するようにインデックスを調整
        n_rob = min(length(t), length(up_robM));
        handles.h_rob_up = stairs(t(1:n_rob), up_robM(1:n_rob), 'LineWidth', 2);
        handles.h_rob_low = stairs(t(1:n_rob), low_robM(1:n_rob), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Upper robustness','Lower robustness'}, 'Location','best');
        xlabel('time');
        ylabel('Robustness');
        % title を削除（不要）
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        hold off;

        % ====== (N+2) 因果関係をプロット ======
        handles.ax_cau = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        % データ長が一致するようにインデックスを調整
        n_cau = min(length(t), length(up_optCau));
        handles.h_cau_up = stairs(t(1:n_cau), up_optCau(1:n_cau), 'LineWidth', 2);
        handles.h_cau_low = stairs(t(1:n_cau), low_optCau(1:n_cau), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Violation causation','Satisfaction causation'}, 'Location','best');
        xlabel('time');
        ylabel('Causation');
        % title を削除（不要）
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        hold off;

        % ====== 全体のタイトル ======
        sgtitle(strrep(phi_str, '_', '\_'), 'Interpreter', 'none', 'FontSize', 16, 'FontWeight', 'bold');

        % ★ 作成したすべてのハンドルをFigureに保存する
        setappdata(figHandle, 'PlotHandles', handles);

    else
        % --- 2回目以降の呼び出し (Figureが存在する場合) ---
        % グラフを再描画せず、データ (XData, YData) のみを更新する

        % 既存のFigureを一番手前に持ってくる (ウィンドウが後ろにいかないように)
        figure(figHandle);

        % ★ Figureに保存したハンドル構造体を取得
        handles = getappdata(figHandle, 'PlotHandles');

        % ====== (1..N) Signals データを更新 ======
        for s = 1:num_signals
            % ハンドルが存在するか確認 (念のため)
            if isgraphics(handles.h_sig(s))
                set(handles.h_sig(s), 'XData', t, 'YData', trace(s+1,:));
                % 軸の自動スケール (Y軸のみ更新、X軸は伸びるように)
                set(handles.ax_sig(s), 'XLimMode', 'auto', 'YLimMode', 'auto', 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
            else
                warning('Signal plot handle %d is invalid.', s);
            end
        end

        % ====== (N+1) Robustness データを更新 ======
         if isgraphics(handles.h_rob_up) && isgraphics(handles.h_rob_low)
            n_rob = min(length(t), length(up_robM));
            set(handles.h_rob_up, 'XData', t(1:n_rob), 'YData', up_robM(1:n_rob));
            set(handles.h_rob_low, 'XData', t(1:n_rob), 'YData', low_robM(1:n_rob));
            set(handles.ax_rob, 'XLimMode', 'auto', 'YLimMode', 'auto', 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
         else
             warning('Robustness plot handle is invalid.');
         end

        % ====== (N+2) Causation データを更新 ======
        if isgraphics(handles.h_cau_up) && isgraphics(handles.h_cau_low)
            n_cau = min(length(t), length(up_optCau));
            set(handles.h_cau_up, 'XData', t(1:n_cau), 'YData', up_optCau(1:n_cau));
            set(handles.h_cau_low, 'XData', t(1:n_cau), 'YData', low_optCau(1:n_cau));
            set(handles.ax_cau, 'XLimMode', 'auto', 'YLimMode', 'auto', 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        else
             warning('Causation plot handle is invalid.');
        end

        % ★ 変更を画面に反映
        drawnow limitrate; % limitrate オプションで更新頻度を調整
    end

    % ====== Save Figure (毎回上書き保存) ======
    if ~isempty(outfile) && isgraphics(figHandle)
        try
            exportgraphics(figHandle, outfile, 'Resolution', 300);
        catch ME
            warning('Failed to save figure to %s: %s', outfile, ME.message);
        end
    end
end