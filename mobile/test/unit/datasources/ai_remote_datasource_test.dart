import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

import 'package:mobile/data/datasources/remote/ai_remote_datasource.dart';

import '../../helpers/mocks.dart';

void main() {
  late MockDio dio;
  late AiRemoteDataSource datasource;

  setUpAll(() {
    registerFallbackValue(Options());
  });

  setUp(() {
    dio = MockDio();
    datasource = AiRemoteDataSource(dio);
  });

  test('decodes an SSE event when UTF-8 bytes span network chunks', () async {
    const payload = 'data: {"type":"status","message":"O\u02bbzbekiston"}\n\n';
    final bytes = Uint8List.fromList(utf8.encode(payload));
    final multiByteStart = bytes.indexWhere((byte) => byte >= 0xC0);
    expect(multiByteStart, greaterThanOrEqualTo(0));

    final body = ResponseBody(
      Stream.fromIterable([
        Uint8List.sublistView(bytes, 0, multiByteStart + 1),
        Uint8List.sublistView(bytes, multiByteStart + 1),
      ]),
      200,
    );
    when(
      () => dio.post<ResponseBody>(
        '/ai/chat/stream',
        data: any(named: 'data'),
        options: any(named: 'options'),
      ),
    ).thenAnswer(
      (_) async => Response<ResponseBody>(
        data: body,
        statusCode: 200,
        requestOptions: RequestOptions(path: '/ai/chat/stream'),
      ),
    );

    final events = await datasource.sendMessageStream('Savol', null).toList();

    expect(events, [
      {'type': 'status', 'message': 'O\u02bbzbekiston'},
    ]);
  });
}
